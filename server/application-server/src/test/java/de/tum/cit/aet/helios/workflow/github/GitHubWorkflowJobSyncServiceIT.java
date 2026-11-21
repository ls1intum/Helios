package de.tum.cit.aet.helios.workflow.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Replays a real failure from production against a real PostgreSQL: Artemis' CI reports a job
 * whose {@code name:} is an expression GitHub could not evaluate, so the job "name" is the raw
 * 445-character expression. It does not fit the {@code varchar(255)} column, the insert failed and
 * the whole job event was dropped.
 */
class GitHubWorkflowJobSyncServiceIT extends HeliosIntegrationTest {

  private static final long REPOSITORY_ID = 1L;
  private static final long WORKFLOW_ID = 51L;
  private static final long RUN_ID = 61L;
  private static final long JOB_ID = 71L;

  private static final String EXPRESSION_PREFIX =
      "github.event.action == 'edited' && !github.event.changes.base && "
          + "'PR metadata updated (validation unchanged)' || ";

  @Autowired private GitHubWorkflowJobSyncService syncService;

  private JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, 'ls1intum/repo-a')",
        REPOSITORY_ID);
    jdbc.update(
        "INSERT INTO workflow (id, repository_id, state, label, name) "
            + "VALUES (?, ?, 'ACTIVE', 'NONE', 'CI')",
        WORKFLOW_ID,
        REPOSITORY_ID);
    jdbc.update(
        "INSERT INTO workflow_run (id, repository_id, workflow_id, run_attempt, run_number, "
            + "status, head_branch, head_sha, created_at, updated_at) "
            + "VALUES (?, ?, ?, 1, 1, 'IN_PROGRESS', 'main', 'deadbeef', now(), now())",
        RUN_ID,
        REPOSITORY_ID,
        WORKFLOW_ID);
  }

  @Test
  void aJobWithAnOverlongNameIsStoredWithTheNameCutToTheColumnLength() {
    String overlongName = (EXPRESSION_PREFIX + "'x' || ".repeat(100)).substring(0, 445);
    assertEquals(445, overlongName.length());

    syncService.syncFromPayload(payload(overlongName));

    String stored =
        jdbc.queryForObject("SELECT name FROM workflow_job WHERE id = ?", String.class, JOB_ID);
    assertEquals(255, stored.length());
    assertTrue(overlongName.startsWith(stored));
    assertEquals(
        "IN_PROGRESS",
        jdbc.queryForObject("SELECT status FROM workflow_job WHERE id = ?", String.class, JOB_ID));
  }

  @Test
  void aJobWithANormalNameIsStoredUnchanged() {
    syncService.syncFromPayload(payload("Build / Build .war artifact"));

    assertEquals(
        "Build / Build .war artifact",
        jdbc.queryForObject("SELECT name FROM workflow_job WHERE id = ?", String.class, JOB_ID));
  }

  private static GitHubWorkflowJobPayload payload(String jobName) {
    var job =
        new GitHubWorkflowJobPayload.WorkflowJob(
            JOB_ID,
            RUN_ID,
            "CI",
            "main",
            "deadbeef",
            "https://github.com/ls1intum/repo-a/actions/runs/61/job/71",
            "in_progress",
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null,
            jobName);
    return new GitHubWorkflowJobPayload("in_progress", job, null, null);
  }
}
