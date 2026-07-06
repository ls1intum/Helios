package de.tum.cit.aet.helios.workflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * Cross-repository write guard for {@code PUT /api/workflows/{id}/label}. The endpoint is
 * {@code @EnforceAtLeastMaintainer}, so a maintainer of one repository could previously relabel a
 * workflow in another repository (the label lookup used an unscoped {@code findById}). The update
 * must be scoped to the current {@code X-REPOSITORY-ID}: a foreign or context-less workflow id is
 * rejected and the foreign workflow's label is never mutated.
 */
class WorkflowLabelScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;
  private static final long WF_B = 52L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertWorkflow(jdbc, WF_B, REPO_B);
  }

  @Test
  void relabelingAWorkflowFromAnotherRepositoryIsRejectedAndDoesNotMutateIt() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    // Maintainer acting in repo A tries to relabel repo B's workflow → not found (400), no mutation.
    mockMvc
        .perform(
            put("/api/workflows/{id}/label", WF_B)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"TEST\""))
        .andExpect(status().isBadRequest());
    assertLabel(jdbc, WF_B, "NONE");
  }

  @Test
  void relabelingWithoutRepositoryContextIsRejectedAndDoesNotMutate() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    // No X-REPOSITORY-ID → null context → must not fall back to an unscoped write.
    mockMvc
        .perform(
            put("/api/workflows/{id}/label", WF_B)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"TEST\""))
        .andExpect(status().isBadRequest());
    assertLabel(jdbc, WF_B, "NONE");
  }

  @Test
  void relabelingAWorkflowInTheCurrentRepositorySucceeds() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    mockMvc
        .perform(
            put("/api/workflows/{id}/label", WF_B)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .header(X_REPOSITORY_ID, String.valueOf(REPO_B))
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"TEST\""))
        .andExpect(status().isNoContent());
    assertLabel(jdbc, WF_B, "TEST");
  }

  private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor maintainer() {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .authorities(new SimpleGrantedAuthority("ROLE_MAINTAINER"));
  }

  private static void assertLabel(JdbcTemplate jdbc, long workflowId, String expected) {
    String actual =
        jdbc.queryForObject("SELECT label FROM workflow WHERE id = ?", String.class, workflowId);
    if (!expected.equals(actual)) {
      throw new AssertionError(
          "expected workflow " + workflowId + " label " + expected + " but found " + actual);
    }
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
}
