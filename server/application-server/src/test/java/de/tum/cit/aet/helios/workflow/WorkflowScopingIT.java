package de.tum.cit.aet.helios.workflow;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cross-refactor guard: {@code GET /api/workflows*} must return only the current repository's
 * workflows (scoped by {@code X-REPOSITORY-ID}). Passes with the legacy gitRepositoryFilter and
 * after the switch to explicit per-query filtering; also asserts the {@code findById} path no longer
 * leaks cross-repo and that a missing repository context yields an empty list (never findAll).
 */
class WorkflowScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;
  private static final long WF_A1 = 11L;
  private static final long WF_A2 = 12L;
  private static final long WF_B1 = 21L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertWorkflow(jdbc, WF_A1, REPO_A, "CI");
    insertWorkflow(jdbc, WF_A2, REPO_A, "Deploy");
    insertWorkflow(jdbc, WF_B1, REPO_B, "CI");
  }

  @Test
  void workflowsAreScopedToRepositoryA() throws Exception {
    mockMvc
        .perform(get("/api/workflows").header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].repository.id", everyItem(equalTo((int) REPO_A))));
  }

  @Test
  void workflowsByStateAreScopedToRepositoryA() throws Exception {
    mockMvc
        .perform(get("/api/workflows/state/ACTIVE").header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].repository.id", everyItem(equalTo((int) REPO_A))));
  }

  @Test
  void workflowByIdIsInvisibleFromAnotherRepository() throws Exception {
    mockMvc
        .perform(get("/api/workflows/{id}", WF_B1).header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/workflows/{id}", WF_B1).header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk());
  }

  @Test
  void withoutRepositoryContextReturnsEmpty() throws Exception {
    mockMvc
        .perform(get("/api/workflows"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private static void insertRepo(JdbcTemplate jdbc, long id, String nameWithOwner) {
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, ?)",
        id,
        nameWithOwner);
  }

  private static void insertWorkflow(JdbcTemplate jdbc, long id, long repositoryId, String name) {
    jdbc.update(
        "INSERT INTO workflow (id, repository_id, name, state) VALUES (?, ?, ?, 'ACTIVE')",
        id,
        repositoryId,
        name);
  }
}
