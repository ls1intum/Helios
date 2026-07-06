package de.tum.cit.aet.helios.workflow.pipeline;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end guard for {@code GET /api/pipeline/branch}. With no persisted config, a repo's
 * pipeline is auto-detected from its observed CI jobs into the Build/Test/Quality lanes, and each
 * detected node aggregates the matching head-commit jobs. No {@code X-REPOSITORY-ID} → empty.
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
    insertJob(jdbc, 101, REPO, RUN, "Build / Build .war artifact", "COMPLETED", "SUCCESS");
    insertJob(jdbc, 103, REPO, RUN, "Test / Client Tests", "COMPLETED", "FAILURE");
  }

  @Test
  void pipelineIsAutoDetectedFromJobsIntoBuildTestQualityLanes() throws Exception {
    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", BRANCH)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        // The three canonical lanes are always present.
        .andExpect(jsonPath("$.categories[*].name", hasItem("Build")))
        .andExpect(jsonPath("$.categories[*].name", hasItem("Test")))
        .andExpect(jsonPath("$.categories[*].name", hasItem("Quality")))
        // Detected Build node aggregates its matching job (completed/success).
        .andExpect(
            jsonPath(
                "$.categories[?(@.name=='Build')].nodes[?(@.label=='Build .war artifact')].status",
                hasItem("COMPLETED")))
        .andExpect(
            jsonPath(
                "$.categories[?(@.name=='Build')].nodes[?(@.label=='Build .war artifact')]"
                    + ".conclusion",
                hasItem("SUCCESS")))
        // Detected Test node aggregates its matching job (completed/failure).
        .andExpect(
            jsonPath(
                "$.categories[?(@.name=='Test')].nodes[?(@.label=='Client Tests')].status",
                hasItem("COMPLETED")))
        .andExpect(
            jsonPath(
                "$.categories[?(@.name=='Test')].nodes[?(@.label=='Client Tests')].conclusion",
                hasItem("FAILURE")));
  }

  @Test
  void withoutRepositoryContextPipelineIsEmpty() throws Exception {
    mockMvc
        .perform(get("/api/pipeline/branch").param("branch", BRANCH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(0));
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
        "INSERT INTO workflow_run (id, repository_id, workflow_id, run_attempt, run_number, name, "
            + "status, head_branch, head_sha, created_at, updated_at) "
            + "VALUES (?, ?, ?, 1, ?, 'CI', 'COMPLETED', ?, ?, now(), now())",
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
