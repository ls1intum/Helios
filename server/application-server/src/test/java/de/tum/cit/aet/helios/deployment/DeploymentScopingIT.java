package de.tum.cit.aet.helios.deployment;

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
 * Cross-repository isolation guard for {@code /api/deployments*}. Deployments are keyed by id,
 * environment id, and pull-request id; the removed Hibernate {@code gitRepositoryFilter} used to
 * scope the derived {@code Deployment} queries to the current repository, so these endpoints must
 * now enforce it explicitly. Because {@code GET /api/**} is unauthenticated, the no-header case is
 * exercised too (a missing {@code X-REPOSITORY-ID} must not fall back to an unscoped PK load).
 */
class DeploymentScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;
  private static final long ENV_A = 11L;
  private static final long ENV_B = 21L;
  private static final long DEPLOY_A = 101L;
  private static final long DEPLOY_B = 201L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertEnv(jdbc, ENV_A, REPO_A, "a-staging");
    insertEnv(jdbc, ENV_B, REPO_B, "b-staging");
    insertDeployment(jdbc, DEPLOY_A, ENV_A, REPO_A);
    insertDeployment(jdbc, DEPLOY_B, ENV_B, REPO_B);
  }

  @Test
  void deploymentsListIsScopedToCurrentRepository() throws Exception {
    mockMvc
        .perform(get("/api/deployments").header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[*].repository.id", everyItem(equalTo((int) REPO_A))));
    mockMvc
        .perform(get("/api/deployments").header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].repository.id").value((int) REPO_B));
  }

  @Test
  void deploymentsListWithoutHeaderIsEmpty() throws Exception {
    mockMvc
        .perform(get("/api/deployments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void deploymentByIdIsInvisibleFromAnotherRepository() throws Exception {
    mockMvc
        .perform(
            get("/api/deployments/{id}", DEPLOY_B).header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/deployments/{id}", DEPLOY_B).header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk());
  }

  @Test
  void deploymentByIdWithoutHeaderIsNotFound() throws Exception {
    // No header → null context → must not fall back to an unscoped findById (anonymous IDOR).
    mockMvc
        .perform(get("/api/deployments/{id}", DEPLOY_A))
        .andExpect(status().isNotFound());
  }

  @Test
  void deploymentsByForeignEnvironmentAreEmpty() throws Exception {
    // ENV_B belongs to repo B: scoping to repo A must not surface repo B's deployments.
    mockMvc
        .perform(
            get("/api/deployments/environment/{envId}", ENV_B)
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            get("/api/deployments/environment/{envId}", ENV_B)
                .header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void latestDeploymentByForeignEnvironmentIsNotFound() throws Exception {
    mockMvc
        .perform(
            get("/api/deployments/environment/{envId}/latest", ENV_B)
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/deployments/environment/{envId}/latest", ENV_B)
                .header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk());
  }

  @Test
  void activityHistoryByForeignEnvironmentIsEmpty() throws Exception {
    mockMvc
        .perform(
            get("/api/deployments/environment/{envId}/activity-history", ENV_B)
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            get("/api/deployments/environment/{envId}/activity-history", ENV_B)
                .header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
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
    jdbc.update(
        "INSERT INTO environment (id, repository_id, enabled, locked, name) "
            + "VALUES (?, ?, true, false, ?)",
        id,
        repositoryId,
        name);
  }

  private static void insertDeployment(
      JdbcTemplate jdbc, long id, long environmentId, long repositoryId) {
    jdbc.update(
        "INSERT INTO deployment (id, environment_id, repository_id, created_at, state, ref, sha, "
            + "url) VALUES (?, ?, ?, now(), 'SUCCESS', 'main', 'deadbeef', 'https://example/dep')",
        id,
        environmentId,
        repositoryId);
  }
}
