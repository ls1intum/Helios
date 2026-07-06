package de.tum.cit.aet.helios.releaseinfo;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cross-repository isolation guard for {@code GET /api/release-info}. Release candidates are scoped
 * by the current repository; with {@code GET /api/**} unauthenticated, a missing
 * {@code X-REPOSITORY-ID} must return an empty list (never a cross-repo or unscoped read).
 */
class ReleaseInfoScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertCommit(jdbc, "shaA", REPO_A);
    insertCommit(jdbc, "shaB", REPO_B);
    insertReleaseCandidate(jdbc, 81L, REPO_A, "rc-a1", "shaA");
    insertReleaseCandidate(jdbc, 82L, REPO_A, "rc-a2", "shaA");
    insertReleaseCandidate(jdbc, 91L, REPO_B, "rc-b1", "shaB");
  }

  @Test
  void releaseInfosAreScopedToRepositoryA() throws Exception {
    mockMvc
        .perform(get("/api/release-info").header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].name", containsInAnyOrder("rc-a1", "rc-a2")));
  }

  @Test
  void releaseInfosAreScopedToRepositoryB() throws Exception {
    mockMvc
        .perform(get("/api/release-info").header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("rc-b1"));
  }

  @Test
  void releaseInfosWithoutHeaderAreEmpty() throws Exception {
    mockMvc
        .perform(get("/api/release-info"))
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

  private static void insertCommit(JdbcTemplate jdbc, String sha, long repositoryId) {
    jdbc.update("INSERT INTO commit (sha, repository_id) VALUES (?, ?)", sha, repositoryId);
  }

  private static void insertReleaseCandidate(
      JdbcTemplate jdbc, long id, long repositoryId, String name, String commitSha) {
    jdbc.update(
        "INSERT INTO release_candidate (id, repository_id, name, created_at, commit_repository_id, "
            + "commit_sha) VALUES (?, ?, ?, now(), ?, ?)",
        id,
        repositoryId,
        name,
        repositoryId,
        commitSha);
  }
}
