package de.tum.cit.aet.helios.tests;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cross-repository isolation guard for {@code /api/tests/*}. Test results are reached via a pull
 * request or workflow-run id; a foreign id must not surface another repository's test results. The
 * entry lookups are scoped, so cross-repo and no-header (unauthenticated) requests must 404.
 */
class TestResultScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;
  private static final long PR_B = 41L;
  private static final long WF_B = 52L;
  private static final long RUN_B = 71L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertDefaultBranch(jdbc, REPO_B, "main");
    insertPr(jdbc, PR_B, REPO_B, 1, "b-pr");
    insertWorkflow(jdbc, WF_B, REPO_B);
    insertRun(jdbc, RUN_B, REPO_B, WF_B);
  }

  @Test
  void testResultsForPullRequestAreScopedByRepository() throws Exception {
    // Own repo → 200 (well-formed, empty result set — no linked runs).
    mockMvc
        .perform(get("/api/tests/pr/{id}", PR_B).header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testResults.length()").value(0));
    // A PR id from another repository must not surface its test results.
    mockMvc
        .perform(get("/api/tests/pr/{id}", PR_B).header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isNotFound());
    // No header → null context → 404 (anonymous, since GET /api/** is permitAll).
    mockMvc.perform(get("/api/tests/pr/{id}", PR_B)).andExpect(status().isNotFound());
  }

  @Test
  void testResultsForWorkflowRunAreScopedByRepository() throws Exception {
    mockMvc
        .perform(get("/api/tests/run/{id}", RUN_B).header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testResults.length()").value(0));
    mockMvc
        .perform(get("/api/tests/run/{id}", RUN_B).header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/tests/run/{id}", RUN_B)).andExpect(status().isNotFound());
  }

  private static void insertRepo(JdbcTemplate jdbc, long id, String nameWithOwner) {
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, ?)",
        id,
        nameWithOwner);
  }

  private static void insertDefaultBranch(JdbcTemplate jdbc, long repositoryId, String name) {
    jdbc.update(
        "INSERT INTO branch (name, repository_id, ahead_by, behind_by, is_default, protection) "
            + "VALUES (?, ?, 0, 0, true, false)",
        name,
        repositoryId);
  }

  private static void insertPr(JdbcTemplate jdbc, long id, long repositoryId, int number,
      String title) {
    jdbc.update(
        "INSERT INTO issue (id, repository_id, issue_type, number, additions, deletions, "
            + "changed_files, commits, comments_count, is_locked, is_draft, is_merged, "
            + "is_mergeable, maintainer_can_modify, state, title, html_url, head_sha, created_at, "
            + "updated_at) VALUES (?, ?, 'PULL_REQUEST', ?, 0, 0, 0, 0, 0, false, false, false, "
            + "false, false, 'OPEN', ?, ?, 'prsha', now(), now())",
        id,
        repositoryId,
        number,
        title,
        "https://example/pr/" + id);
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
