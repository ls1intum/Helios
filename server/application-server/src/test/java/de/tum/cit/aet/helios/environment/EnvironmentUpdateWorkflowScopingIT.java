package de.tum.cit.aet.helios.environment;

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
 * Cross-repository write guard for {@code PUT /api/environments/{id}} (maintainer-gated). Updating
 * an environment can attach a deployment workflow by id; that lookup previously used an unscoped
 * {@code findById}, so a maintainer could bind a workflow from another repository to their
 * environment. The attach must be scoped to the environment's repository: a foreign workflow id is
 * rejected (404) and no foreign workflow is bound.
 */
class EnvironmentUpdateWorkflowScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;
  private static final long ENV_A = 11L;
  private static final long WF_A = 51L;
  private static final long WF_B = 52L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertEnv(jdbc, ENV_A, REPO_A, "a-staging");
    insertWorkflow(jdbc, WF_A, REPO_A);
    insertWorkflow(jdbc, WF_B, REPO_B);
  }

  @Test
  void attachingADeploymentWorkflowFromAnotherRepositoryIsRejectedAndDoesNotAttach()
      throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    mockMvc
        .perform(
            put("/api/environments/{id}", ENV_A)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(ENV_A, "a-staging", WF_B)))
        .andExpect(status().isNotFound());

    assertDeploymentWorkflow(jdbc, ENV_A, null);
  }

  @Test
  void attachingADeploymentWorkflowInTheCurrentRepositorySucceeds() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    mockMvc
        .perform(
            put("/api/environments/{id}", ENV_A)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(ENV_A, "a-staging", WF_A)))
        .andExpect(status().isOk());

    assertDeploymentWorkflow(jdbc, ENV_A, WF_A);
  }

  private static String body(long envId, String name, long deploymentWorkflowId) {
    return "{\"id\":" + envId + ",\"name\":\"" + name + "\",\"enabled\":true,"
        + "\"deploymentWorkflow\":{\"id\":" + deploymentWorkflowId + "}}";
  }

  private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor maintainer() {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .authorities(new SimpleGrantedAuthority("ROLE_MAINTAINER"));
  }

  private static void assertDeploymentWorkflow(JdbcTemplate jdbc, long envId, Long expected) {
    Long actual =
        jdbc.queryForObject(
            "SELECT deployment_workflow_id FROM environment WHERE id = ?", Long.class, envId);
    boolean matches = expected == null ? actual == null : expected.equals(actual);
    if (!matches) {
      throw new AssertionError(
          "expected environment " + envId + " deployment_workflow_id " + expected
              + " but found " + actual);
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

  private static void insertEnv(JdbcTemplate jdbc, long id, long repositoryId, String name) {
    // version=0: Environment is @Version-optimistic-locked, and the update path increments it on
    // commit (version.intValue()), so a raw insert must seed a non-null version.
    jdbc.update(
        "INSERT INTO environment (id, repository_id, enabled, locked, name, version) "
            + "VALUES (?, ?, true, false, ?, 0)",
        id,
        repositoryId,
        name);
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
