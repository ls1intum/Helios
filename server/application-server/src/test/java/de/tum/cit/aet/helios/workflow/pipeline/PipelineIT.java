package de.tum.cit.aet.helios.workflow.pipeline;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end guard for the canonical pipeline endpoint {@code GET /api/pipeline/branch}. Seeds one
 * head-commit run with jobs matching several configured nodes and asserts: every canonical node is
 * always present; matched nodes aggregate to the right status/conclusion; unmatched nodes are
 * PENDING; and with no {@code X-REPOSITORY-ID} the runs are unscoped away, so all nodes are PENDING
 * (never a cross-repository leak).
 */
class PipelineIT extends HeliosIntegrationTest {

  private static final long REPO = 1L;
  private static final long WF = 51L;
  private static final long RUN = 61L;
  private static final String BRANCH = "main";
  private static final String SHA = "deadbeef";

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO, "ls1intum/repo-a");
    insertBranch(jdbc, REPO, BRANCH, SHA);
    insertWorkflow(jdbc, WF, REPO);
    insertRun(jdbc, RUN, REPO, WF, BRANCH, SHA);
    // Build: native done+ok, docker running. Tests: client failed, server ok, e2e absent.
    // Quality: client ok, server absent.
    insertJob(jdbc, 101, REPO, RUN, "Build / Build .war artifact", "COMPLETED", "SUCCESS");
    insertJob(
        jdbc, 102, REPO, RUN, "Build / Build and Push Docker Image (PR, amd64)", "IN_PROGRESS",
        null);
    insertJob(jdbc, 103, REPO, RUN, "Test / Client Tests", "COMPLETED", "FAILURE");
    insertJob(jdbc, 104, REPO, RUN, "Test / Server Tests (PostgreSQL)", "COMPLETED", "SUCCESS");
    insertJob(jdbc, 105, REPO, RUN, "Quality / Client Code Style", "COMPLETED", "SUCCESS");
  }

  @Test
  void pipelineAggregatesJobsIntoCanonicalNodes() throws Exception {
    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", BRANCH)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(3))
        // Build
        .andExpect(jsonPath("$.categories[0].name").value("Build"))
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").value("SUCCESS"))
        .andExpect(jsonPath("$.categories[0].nodes[1].key").value("build-docker"))
        .andExpect(jsonPath("$.categories[0].nodes[1].status").value("IN_PROGRESS"))
        // Tests
        .andExpect(jsonPath("$.categories[1].name").value("Tests"))
        .andExpect(jsonPath("$.categories[1].nodes[0].key").value("test-client"))
        .andExpect(jsonPath("$.categories[1].nodes[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.categories[1].nodes[0].conclusion").value("FAILURE"))
        .andExpect(jsonPath("$.categories[1].nodes[1].key").value("test-server"))
        .andExpect(jsonPath("$.categories[1].nodes[1].conclusion").value("SUCCESS"))
        // e2e has no matching job → always visible, pending
        .andExpect(jsonPath("$.categories[1].nodes[2].key").value("test-e2e"))
        .andExpect(jsonPath("$.categories[1].nodes[2].status").value("PENDING"))
        .andExpect(jsonPath("$.categories[1].nodes[2].conclusion").doesNotExist())
        // Quality: client matched, server pending
        .andExpect(jsonPath("$.categories[2].name").value("Quality"))
        .andExpect(jsonPath("$.categories[2].nodes[0].key").value("quality-client"))
        .andExpect(jsonPath("$.categories[2].nodes[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.categories[2].nodes[1].key").value("quality-server"))
        .andExpect(jsonPath("$.categories[2].nodes[1].status").value("PENDING"));
  }

  @Test
  void withoutRepositoryContextAllNodesArePending() throws Exception {
    // No X-REPOSITORY-ID → the branch/run lookup is unscoped away → no jobs → every node pending,
    // structure still fully present (never leaks another repo's runs).
    mockMvc
        .perform(get("/api/pipeline/branch").param("branch", BRANCH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(3))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("PENDING"))
        .andExpect(jsonPath("$.categories[1].nodes[1].status").value("PENDING"))
        .andExpect(jsonPath("$.categories[2].nodes[0].status").value("PENDING"));
  }

  private static void insertRepo(JdbcTemplate jdbc, long id, String nameWithOwner) {
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, ?)",
        id,
        nameWithOwner);
  }

  private static void insertBranch(JdbcTemplate jdbc, long repositoryId, String name, String sha) {
    jdbc.update(
        "INSERT INTO branch (repository_id, name, ahead_by, behind_by, is_default, protection, "
            + "commit_sha) VALUES (?, ?, 0, 0, true, false, ?)",
        repositoryId,
        name,
        sha);
  }

  private static void insertWorkflow(JdbcTemplate jdbc, long id, long repositoryId) {
    jdbc.update(
        "INSERT INTO workflow (id, repository_id, state, label, name) "
            + "VALUES (?, ?, 'ACTIVE', 'NONE', ?)",
        id,
        repositoryId,
        "wf-" + id);
  }

  private static void insertRun(
      JdbcTemplate jdbc, long id, long repositoryId, long workflowId, String branch, String sha) {
    jdbc.update(
        "INSERT INTO workflow_run (id, repository_id, workflow_id, run_attempt, run_number, "
            + "status, head_branch, head_sha, created_at, updated_at) "
            + "VALUES (?, ?, ?, 1, ?, 'COMPLETED', ?, ?, now(), now())",
        id,
        repositoryId,
        workflowId,
        id,
        branch,
        sha);
  }

  private static void insertJob(
      JdbcTemplate jdbc,
      long id,
      long repositoryId,
      long runId,
      String name,
      String statusValue,
      String conclusionValue) {
    jdbc.update(
        "INSERT INTO workflow_job (id, repository_id, workflow_run_id, name, workflow_name, "
            + "status, conclusion, html_url) VALUES (?, ?, ?, ?, 'CI', ?, ?, ?)",
        id,
        repositoryId,
        runId,
        name,
        statusValue,
        conclusionValue,
        "https://example/job/" + id);
  }
}
