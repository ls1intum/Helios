package de.tum.cit.aet.helios.environment;

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
 * Cross-refactor guard: {@code GET /api/environments*} must return only the current repository's
 * environments (scoped by the {@code X-REPOSITORY-ID} header). This passes with the legacy
 * Hibernate {@code gitRepositoryFilter} and must keep passing after the switch to explicit
 * per-query filtering — it exercises the real request path (interceptor → OSIV → repository).
 */
class EnvironmentScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;
  private static final long ENV_A1 = 11L;
  private static final long ENV_A2 = 12L;
  private static final long ENV_B1 = 21L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertEnabledEnv(jdbc, ENV_A1, REPO_A, "a-staging");
    insertEnabledEnv(jdbc, ENV_A2, REPO_A, "a-production");
    insertEnabledEnv(jdbc, ENV_B1, REPO_B, "b-production");
  }

  @Test
  void enabledEnvironmentsAreScopedToRepositoryA() throws Exception {
    mockMvc
        .perform(get("/api/environments/enabled").header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].repository.id", everyItem(equalTo((int) REPO_A))));
  }

  @Test
  void enabledEnvironmentsAreScopedToRepositoryB() throws Exception {
    mockMvc
        .perform(get("/api/environments/enabled").header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].repository.id").value((int) REPO_B));
  }

  @Test
  void allEnvironmentsAreScopedToCurrentRepository() throws Exception {
    mockMvc
        .perform(get("/api/environments").header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].repository.id", everyItem(equalTo((int) REPO_A))));
  }

  @Test
  void environmentByIdIsInvisibleFromAnotherRepository() throws Exception {
    // ENV_B1 belongs to repo B: invisible when scoped to repo A, visible when scoped to repo B.
    mockMvc
        .perform(
            get("/api/environments/{id}", ENV_B1).header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/environments/{id}", ENV_B1).header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk());
  }

  @Test
  void withoutRepositoryContextReturnsEmpty() throws Exception {
    // No X-REPOSITORY-ID header → a tenant-scoped read has no repository → empty (never findAll).
    mockMvc
        .perform(get("/api/environments/enabled"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(get("/api/environments"))
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

  private static void insertEnabledEnv(JdbcTemplate jdbc, long id, long repositoryId, String name) {
    jdbc.update(
        "INSERT INTO environment (id, repository_id, enabled, locked, name) "
            + "VALUES (?, ?, true, false, ?)",
        id,
        repositoryId,
        name);
  }
}
