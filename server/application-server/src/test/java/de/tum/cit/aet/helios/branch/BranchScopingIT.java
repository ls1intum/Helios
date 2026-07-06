package de.tum.cit.aet.helios.branch;

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
 * Cross-refactor guard: {@code GET /api/branches} must return only the current repository's
 * branches (scoped by {@code X-REPOSITORY-ID}). Passes with the legacy gitRepositoryFilter and must
 * keep passing after the switch to explicit per-query filtering.
 */
class BranchScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertBranch(jdbc, REPO_A, "a-main");
    insertBranch(jdbc, REPO_A, "a-feature");
    insertBranch(jdbc, REPO_B, "b-main");
  }

  @Test
  void branchesAreScopedToRepositoryA() throws Exception {
    mockMvc
        .perform(get("/api/branches").header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].repository.id", everyItem(equalTo((int) REPO_A))));
  }

  @Test
  void branchesAreScopedToRepositoryB() throws Exception {
    mockMvc
        .perform(get("/api/branches").header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].repository.id").value((int) REPO_B));
  }

  @Test
  void withoutRepositoryContextReturnsEmpty() throws Exception {
    // No X-REPOSITORY-ID header → a tenant-scoped read has no repository → empty (never findAll).
    mockMvc
        .perform(get("/api/branches"))
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

  private static void insertBranch(JdbcTemplate jdbc, long repositoryId, String name) {
    jdbc.update(
        "INSERT INTO branch (repository_id, name, ahead_by, behind_by, is_default, protection) "
            + "VALUES (?, ?, 0, 0, false, false)",
        repositoryId,
        name);
  }
}
