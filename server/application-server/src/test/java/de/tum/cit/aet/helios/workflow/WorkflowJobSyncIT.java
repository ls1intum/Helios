package de.tum.cit.aet.helios.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobSyncService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies {@link GitHubWorkflowJobSyncService} persists {@code workflow_job} events as
 * {@link WorkflowJob} rows scoped to the parent run's repository, upserts on re-emission, and skips
 * jobs whose parent run is not tracked (so no dangling row / FK violation).
 */
class WorkflowJobSyncIT extends HeliosIntegrationTest {

  private static final long REPO = 1L;
  private static final long WF = 51L;
  private static final long RUN = 61L;
  private static final long JOB = 101L;
  private static final long UNTRACKED_RUN = 999L;

  @Autowired private GitHubWorkflowJobSyncService syncService;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO, "ls1intum/repo-a");
    insertWorkflow(jdbc, WF, REPO);
    insertRun(jdbc, RUN, REPO, WF);
  }

  @Test
  void persistsJobForTrackedRunScopedToItsRepository() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    syncService.syncFromPayload(
        payload(JOB, RUN, "Build / Build .war artifact", "completed", "success"));

    assertEquals(1, count(jdbc, "SELECT count(*) FROM workflow_job WHERE id = " + JOB));
    assertEquals(
        "Build / Build .war artifact",
        jdbc.queryForObject("SELECT name FROM workflow_job WHERE id = ?", String.class, JOB));
    assertEquals(
        "COMPLETED",
        jdbc.queryForObject("SELECT status FROM workflow_job WHERE id = ?", String.class, JOB));
    assertEquals(
        "SUCCESS",
        jdbc.queryForObject("SELECT conclusion FROM workflow_job WHERE id = ?", String.class, JOB));
    assertEquals(
        RUN,
        jdbc.queryForObject(
            "SELECT workflow_run_id FROM workflow_job WHERE id = ?", Long.class, JOB));
    // Scoped to the parent run's repository (inherited tenant association).
    assertEquals(
        REPO,
        jdbc.queryForObject(
            "SELECT repository_id FROM workflow_job WHERE id = ?", Long.class, JOB));
  }

  @Test
  void upsertsOnReEmissionInsteadOfDuplicating() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    syncService.syncFromPayload(payload(JOB, RUN, "Test / Client Tests", "in_progress", null));
    syncService.syncFromPayload(payload(JOB, RUN, "Test / Client Tests", "completed", "failure"));

    assertEquals(1, count(jdbc, "SELECT count(*) FROM workflow_job WHERE id = " + JOB));
    assertEquals(
        "COMPLETED",
        jdbc.queryForObject("SELECT status FROM workflow_job WHERE id = ?", String.class, JOB));
    assertEquals(
        "FAILURE",
        jdbc.queryForObject("SELECT conclusion FROM workflow_job WHERE id = ?", String.class, JOB));
  }

  @Test
  void skipsJobWhoseRunIsNotTracked() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    syncService.syncFromPayload(
        payload(JOB, UNTRACKED_RUN, "Quality / Server Code Style", "completed", "success"));

    assertEquals(0, count(jdbc, "SELECT count(*) FROM workflow_job WHERE id = " + JOB));
  }

  @Test
  void mapsUnknownStatusStringToUnknownEnum() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    syncService.syncFromPayload(payload(JOB, RUN, "E2E / Report", "banana", null));

    assertEquals(
        "UNKNOWN",
        jdbc.queryForObject("SELECT status FROM workflow_job WHERE id = ?", String.class, JOB));
    assertNull(
        jdbc.queryForObject("SELECT conclusion FROM workflow_job WHERE id = ?", String.class, JOB));
  }

  private static GitHubWorkflowJobPayload payload(
      long jobId, long runId, String name, String status, String conclusion) {
    GitHubWorkflowJobPayload.WorkflowJob job =
        new GitHubWorkflowJobPayload.WorkflowJob(
            jobId,
            runId,
            "CI",
            "main",
            "deadbeef",
            "https://example/job/" + jobId,
            status,
            conclusion,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "completed".equals(status) ? OffsetDateTime.now() : null,
            name);
    return new GitHubWorkflowJobPayload("in_progress", job, null, null);
  }

  private static int count(JdbcTemplate jdbc, String sql) {
    Integer c = jdbc.queryForObject(sql, Integer.class);
    return c == null ? 0 : c;
  }

  private static void insertRepo(JdbcTemplate jdbc, long id, String nameWithOwner) {
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, ?)",
        id,
        nameWithOwner);
  }

  private static void insertWorkflow(JdbcTemplate jdbc, long id, long repositoryId) {
    jdbc.update(
        "INSERT INTO workflow (id, repository_id, state, label, name) "
            + "VALUES (?, ?, 'ACTIVE', 'NONE', ?)",
        id,
        repositoryId,
        "wf-" + id);
  }

  private static void insertRun(JdbcTemplate jdbc, long id, long repositoryId, long workflowId) {
    jdbc.update(
        "INSERT INTO workflow_run (id, repository_id, workflow_id, run_attempt, run_number, "
            + "status, head_branch, head_sha, created_at, updated_at) "
            + "VALUES (?, ?, ?, 1, ?, 'COMPLETED', 'main', 'deadbeef', now(), now())",
        id,
        repositoryId,
        workflowId,
        id);
  }
}
