package de.tum.cit.aet.helios.workflow;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cross-repository isolation guard for {@code /api/workflows/runs*}. Detail-by-id scopes with no
 * findById fallback and the paginated query carries a repository predicate; a missing
 * {@code X-REPOSITORY-ID} ({@code GET /api/**} is unauthenticated) must 404 / return empty.
 */
class WorkflowRunScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;
  private static final long WF_A = 51L;
  private static final long WF_B = 52L;
  private static final long RUN_A1 = 61L;
  private static final long RUN_A2 = 62L;
  private static final long RUN_B1 = 71L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertWorkflow(jdbc, WF_A, REPO_A);
    insertWorkflow(jdbc, WF_B, REPO_B);
    insertRun(jdbc, RUN_A1, REPO_A, WF_A);
    insertRun(jdbc, RUN_A2, REPO_A, WF_A);
    insertRun(jdbc, RUN_B1, REPO_B, WF_B);
  }

  @Test
  void workflowRunByIdIsInvisibleFromAnotherRepository() throws Exception {
    mockMvc
        .perform(get("/api/workflows/runs/{id}", RUN_B1).header(X_REPOSITORY_ID, str(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value((int) RUN_B1));
    mockMvc
        .perform(get("/api/workflows/runs/{id}", RUN_B1).header(X_REPOSITORY_ID, str(REPO_A)))
        .andExpect(status().isNotFound());
  }

  @Test
  void workflowRunByIdWithoutHeaderIsNotFound() throws Exception {
    // No header → null context → must not fall back to an unscoped findById (anonymous IDOR).
    mockMvc.perform(get("/api/workflows/runs/{id}", RUN_A1)).andExpect(status().isNotFound());
  }

  @Test
  void paginatedWorkflowRunsAreScopedToCurrentRepository() throws Exception {
    mockMvc
        .perform(get("/api/workflows/runs").param("page", "1").param("size", "50")
            .header(X_REPOSITORY_ID, str(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.runs.length()").value(2))
        .andExpect(jsonPath("$.runs[*].id", hasItems((int) RUN_A1, (int) RUN_A2)));
    mockMvc
        .perform(get("/api/workflows/runs").param("page", "1").param("size", "50")
            .header(X_REPOSITORY_ID, str(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.runs.length()").value(1))
        .andExpect(jsonPath("$.runs[0].id").value((int) RUN_B1));
  }

  @Test
  void paginatedWorkflowRunsWithoutHeaderAreEmpty() throws Exception {
    mockMvc
        .perform(get("/api/workflows/runs").param("page", "1").param("size", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.runs.length()").value(0));
  }

  private static String str(long id) {
    return String.valueOf(id);
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
