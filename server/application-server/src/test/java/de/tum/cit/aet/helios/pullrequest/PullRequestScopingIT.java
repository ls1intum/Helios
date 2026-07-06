package de.tum.cit.aet.helios.pullrequest;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cross-repository isolation guard for {@code /api/pullrequests*}. Detail-by-id scopes explicitly;
 * with {@code GET /api/**} unauthenticated, a missing {@code X-REPOSITORY-ID} must 404, not leak.
 */
class PullRequestScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;
  private static final long PR_A1 = 31L;
  private static final long PR_A2 = 32L;
  private static final long PR_B1 = 41L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertPr(jdbc, PR_A1, REPO_A, 1, "A-first");
    insertPr(jdbc, PR_A2, REPO_A, 2, "A-second");
    insertPr(jdbc, PR_B1, REPO_B, 1, "B-first");
  }

  @Test
  void pullRequestByIdIsInvisibleFromAnotherRepository() throws Exception {
    // PR_B1 belongs to repo B.
    mockMvc
        .perform(
            get("/api/pullrequests/{id}", PR_B1).header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value((int) PR_B1))
        .andExpect(jsonPath("$.repository.id").value((int) REPO_B));
    mockMvc
        .perform(
            get("/api/pullrequests/{id}", PR_B1).header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isNotFound());
  }

  @Test
  void pullRequestWithBodyLoadsInAutoCommitMode() throws Exception {
    // Issue.body is a plain text column (was an accidental @Lob -> oid Large Object). Loading a PR
    // that HAS a body must work over this test's ordinary auto-commit connection; a Large Object
    // would throw "Large Objects may not be used in auto-commit mode". Guards the oid->text fix.
    mockMvc
        .perform(
            get("/api/pullrequests/{id}", PR_B1).header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value((int) PR_B1));
  }

  @Test
  void pullRequestByIdWithoutHeaderIsNotFound() throws Exception {
    // No header → null context → must not fall back to an unscoped findById (anonymous IDOR).
    mockMvc.perform(get("/api/pullrequests/{id}", PR_A1)).andExpect(status().isNotFound());
  }

  @Test
  void pullRequestsByRepositoryIdReturnOnlyThatRepository() throws Exception {
    mockMvc
        .perform(get("/api/pullrequests/repository/{id}", REPO_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].repository.id", everyItem(equalTo((int) REPO_A))));
    mockMvc
        .perform(get("/api/pullrequests/repository/{id}", REPO_B))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value((int) PR_B1))
        .andExpect(jsonPath("$[0].repository.id").value((int) REPO_B));
  }

  @Test
  void paginatedPullRequestsAreScopedToCurrentRepository() throws Exception {
    mockMvc
        .perform(
            get("/api/pullrequests")
                .param("page", "1")
                .param("size", "50")
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalNonPinned").value(2))
        .andExpect(jsonPath("$.page.length()").value(2))
        .andExpect(jsonPath("$.page[*].id", hasItems((int) PR_A1, (int) PR_A2)));
    mockMvc
        .perform(
            get("/api/pullrequests")
                .param("page", "1")
                .param("size", "50")
                .header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalNonPinned").value(1))
        .andExpect(jsonPath("$.page.length()").value(1))
        .andExpect(jsonPath("$.page[0].id").value((int) PR_B1));
  }

  @Test
  void paginatedPullRequestsWithoutHeaderAreEmpty() throws Exception {
    mockMvc
        .perform(get("/api/pullrequests").param("page", "1").param("size", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalNonPinned").value(0))
        .andExpect(jsonPath("$.page.length()").value(0));
  }

  private static void insertRepo(JdbcTemplate jdbc, long id, String nameWithOwner) {
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, ?)",
        id,
        nameWithOwner);
  }

  private static void insertPr(JdbcTemplate jdbc, long id, long repositoryId, int number,
      String title) {
    jdbc.update(
        "INSERT INTO issue (id, repository_id, issue_type, number, additions, deletions, "
            + "changed_files, commits, comments_count, is_locked, is_draft, is_merged, "
            + "is_mergeable, maintainer_can_modify, state, title, html_url, body, created_at, "
            + "updated_at) VALUES "
            + "(?, ?, 'PULL_REQUEST', ?, 0, 0, 0, 0, 0, false, false, false, false, false, "
            + "'OPEN', ?, ?, ?, now(), now())",
        id,
        repositoryId,
        number,
        title,
        "https://example/pr/" + id,
        // A non-null body: if Issue.body were still an @Lob/oid Large Object, loading the PR would
        // throw in this test's auto-commit connection, so each load here guards the oid->text fix.
        "PR body markdown for " + id);
  }
}
