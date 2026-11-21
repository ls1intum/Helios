package de.tum.cit.aet.helios.workflow.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reproduces the duplicate-key race on {@code workflow_run} against a real PostgreSQL.
 *
 * <p>GitHub sends several events for one run within moments, so two threads regularly process the
 * same run at once. Both look the run up, find nothing, and both insert it. The overlap is forced
 * here by making the second GitHub getter that runs between the lookup and the insert wait for the
 * other thread. Without the per-run serialization in {@link GitHubWorkflowRunSyncService} one of
 * the two threads fails with a duplicate key on {@code workflow_run_pkey}.
 */
class GitHubWorkflowRunSyncServiceConcurrencyIT extends HeliosIntegrationTest {

  private static final long REPOSITORY_ID = 1L;
  private static final String REPOSITORY_NAME = "ls1intum/repo-a";
  private static final long WORKFLOW_ID = 51L;
  private static final long FIRST_RUN_ID = 9_000_000L;
  private static final int ITERATIONS = 10;

  /** How long the first thread waits for a partner before it carries on alone. */
  private static final long OVERLAP_WAIT_MILLIS = 150;

  @Autowired private GitHubWorkflowRunSyncService syncService;

  private JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, ?)",
        REPOSITORY_ID,
        REPOSITORY_NAME);
    jdbc.update(
        "INSERT INTO workflow (id, repository_id, state, label, name) "
            + "VALUES (?, ?, 'ACTIVE', 'NONE', 'E2E Tests')",
        WORKFLOW_ID,
        REPOSITORY_ID);
  }

  @Test
  @Timeout(120)
  void concurrentEventsForTheSameRunNeverFailAndStoreItOnce() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int i = 0; i < ITERATIONS; i++) {
        long runId = FIRST_RUN_ID + i;
        CyclicBarrier overlap = new CyclicBarrier(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<?>> attempts = new ArrayList<>();
        for (int thread = 0; thread < 2; thread++) {
          GHWorkflowRun event = githubRun(runId, overlap);
          attempts.add(
              pool.submit(
                  () -> {
                    start.await();
                    return syncService.processRun(event);
                  }));
        }
        start.countDown();

        for (Future<?> attempt : attempts) {
          // A duplicate-key failure surfaces here as an ExecutionException.
          attempt.get(30, TimeUnit.SECONDS);
        }
        assertEquals(1, runCount(runId), "run " + runId + " must be stored exactly once");
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void aRealPostgresDuplicateKeyIsRecognizedAsRetriable() {
    insertRun(FIRST_RUN_ID);

    DataAccessException duplicate =
        assertThrows(DataAccessException.class, () -> insertRun(FIRST_RUN_ID));
    assertTrue(WorkflowRunProcessingGuard.isUniqueViolation(duplicate));

    DataAccessException missingColumns =
        assertThrows(
            DataAccessException.class,
            () -> jdbc.update("INSERT INTO workflow_run (id) VALUES (?)", FIRST_RUN_ID + 1));
    assertFalse(WorkflowRunProcessingGuard.isUniqueViolation(missingColumns));
  }

  private int runCount(long runId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM workflow_run WHERE id = ?", Integer.class, runId);
  }

  private void insertRun(long runId) {
    jdbc.update(
        "INSERT INTO workflow_run (id, repository_id, workflow_id, run_attempt, run_number, "
            + "status, head_branch, head_sha, created_at, updated_at) "
            + "VALUES (?, ?, ?, 1, 1, 'COMPLETED', 'main', 'deadbeef', now(), now())",
        runId,
        REPOSITORY_ID,
        WORKFLOW_ID);
  }

  /**
   * A GitHub workflow run whose pull-request lookup waits for the other thread. That lookup happens
   * after the service has checked whether the run exists and before it saves, which is exactly the
   * window in which the race occurs.
   *
   * <p>The GitHub types are subclassed instead of mocked because their {@code getId()} is a
   * bridge-method-injected accessor that Mockito cannot stub.
   */
  private static GHWorkflowRun githubRun(long runId, CyclicBarrier overlap) throws Exception {
    URL url = URI.create("https://api.github.com/repos/" + REPOSITORY_NAME + "/actions").toURL();
    Date now = new Date();

    GHRepository repository =
        new GHRepository() {
          @Override
          public long getId() {
            return REPOSITORY_ID;
          }

          @Override
          public String getFullName() {
            return REPOSITORY_NAME;
          }
        };

    return new GHWorkflowRun() {
      @Override
      public long getId() {
        return runId;
      }

      @Override
      public Date getCreatedAt() {
        return now;
      }

      @Override
      public Date getUpdatedAt() {
        return now;
      }

      @Override
      public Date getRunStartedAt() {
        return now;
      }

      @Override
      public String getName() {
        return "E2E Tests";
      }

      @Override
      public long getRunNumber() {
        return 1L;
      }

      @Override
      public long getRunAttempt() {
        return 1L;
      }

      @Override
      public URL getHtmlUrl() {
        return url;
      }

      @Override
      public URL getJobsUrl() {
        return url;
      }

      @Override
      public URL getLogsUrl() {
        return url;
      }

      @Override
      public URL getCheckSuiteUrl() {
        return url;
      }

      @Override
      public URL getArtifactsUrl() {
        return url;
      }

      @Override
      public URL getCancelUrl() {
        return url;
      }

      @Override
      public URL getRerunUrl() {
        return url;
      }

      @Override
      public URL getWorkflowUrl() {
        return url;
      }

      @Override
      public String getHeadBranch() {
        return "main";
      }

      @Override
      public String getHeadSha() {
        return "deadbeef";
      }

      @Override
      public Status getStatus() {
        return Status.IN_PROGRESS;
      }

      @Override
      public Conclusion getConclusion() {
        return null;
      }

      @Override
      public long getWorkflowId() {
        return WORKFLOW_ID;
      }

      @Override
      public GHRepository getRepository() {
        return repository;
      }

      @Override
      public List<GHPullRequest> getPullRequests() {
        awaitPartner(overlap);
        return List.of();
      }
    };
  }

  private static void awaitPartner(CyclicBarrier overlap) {
    try {
      overlap.await(OVERLAP_WAIT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException | BrokenBarrierException e) {
      // The other thread is held back (as it should be), or the barrier was already used up.
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
