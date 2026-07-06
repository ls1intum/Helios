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
 * End-to-end guard for {@code GET /api/pipeline/branch}. With no persisted config, a repo's pipeline
 * is auto-detected from its observed CI jobs into the Build/Test/Quality lanes, each detected node
 * aggregating the matching head-commit jobs with distinct states (queued-only = pending; fail-fast
 * to FAILURE). The optional global {@code gate} badge reflects the required-checks job. No
 * {@code X-REPOSITORY-ID} → empty (no leak).
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
        .andExpect(jsonPath("$.categories[*].name", hasItem("Build")))
        .andExpect(jsonPath("$.categories[*].name", hasItem("Test")))
        .andExpect(jsonPath("$.categories[*].name", hasItem("Quality")))
        .andExpect(buildNodeStatus("Build .war artifact", hasItem("COMPLETED")))
        .andExpect(buildNodeConclusion("Build .war artifact", hasItem("SUCCESS")))
        .andExpect(testNodeStatus("Client Tests", hasItem("COMPLETED")))
        .andExpect(testNodeConclusion("Client Tests", hasItem("FAILURE")))
        // No required-checks job seeded → the gate is present but not running yet.
        .andExpect(jsonPath("$.gate.key").value("ci-gate"))
        .andExpect(jsonPath("$.gate.status").value("PENDING"));
  }

  @Test
  void gateReflectsTheRequiredChecksJob() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long run = 62L;
    final String branch = "release";
    final String sha = "d00dfeed";
    insertBranch(jdbc, REPO, branch, sha);
    insertRun(jdbc, run, REPO, WF, branch, sha);
    // The gate maps (globally) to the "All required CI Passed" job in workflow "CI".
    insertJob(jdbc, 205, REPO, run, "All required CI Passed", "COMPLETED", "FAILURE");

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gate.key").value("ci-gate"))
        .andExpect(jsonPath("$.gate.status").value("COMPLETED"))
        .andExpect(jsonPath("$.gate.conclusion").value("FAILURE"));
  }

  @Test
  void nodeStatesQueuedIsPendingAndFailingLegFailsFast() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long run = 63L;
    final String branch = "feature";
    final String sha = "feedface";
    insertBranch(jdbc, REPO, branch, sha);
    insertRun(jdbc, run, REPO, WF, branch, sha);
    // One Build node ("Build .war artifact"): a still-running leg plus an already-failed leg (the
    // "(retry)" suffix is stripped, so both collapse into the same detected node).
    insertJob(jdbc, 301, REPO, run, "Build / Build .war artifact", "IN_PROGRESS", null);
    insertJob(jdbc, 302, REPO, run, "Build / Build .war artifact (retry)", "COMPLETED", "FAILURE");
    // A queued-only E2E node → "not running yet" (PENDING), not a spinner.
    insertJob(jdbc, 303, REPO, run, "E2E / Phase 1", "QUEUED", null);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        // Fail-fast: FAILURE while a leg is still IN_PROGRESS, linking to the failing leg (302).
        .andExpect(buildNodeStatus("Build .war artifact", hasItem("IN_PROGRESS")))
        .andExpect(buildNodeConclusion("Build .war artifact", hasItem("FAILURE")))
        .andExpect(
            jsonPath(
                "$.categories[?(@.name=='Build')].nodes[?(@.label=='Build .war artifact')].htmlUrl",
                hasItem("https://example/job/302")))
        // Queued-only E2E node is pending, not in-progress.
        .andExpect(testNodeStatus("Phase 1", hasItem("PENDING")));
  }

  @Test
  void withoutRepositoryContextPipelineIsEmpty() throws Exception {
    mockMvc
        .perform(get("/api/pipeline/branch").param("branch", BRANCH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(0));
  }

  // --- jsonPath helpers (find category by name, node by label) --------------------------------

  private static org.springframework.test.web.servlet.ResultMatcher buildNodeStatus(
      String label, org.hamcrest.Matcher<Iterable<? super String>> m) {
    return jsonPath("$.categories[?(@.name=='Build')].nodes[?(@.label=='" + label + "')].status", m);
  }

  private static org.springframework.test.web.servlet.ResultMatcher buildNodeConclusion(
      String label, org.hamcrest.Matcher<Iterable<? super String>> m) {
    return jsonPath(
        "$.categories[?(@.name=='Build')].nodes[?(@.label=='" + label + "')].conclusion", m);
  }

  private static org.springframework.test.web.servlet.ResultMatcher testNodeStatus(
      String label, org.hamcrest.Matcher<Iterable<? super String>> m) {
    return jsonPath("$.categories[?(@.name=='Test')].nodes[?(@.label=='" + label + "')].status", m);
  }

  private static org.springframework.test.web.servlet.ResultMatcher testNodeConclusion(
      String label, org.hamcrest.Matcher<Iterable<? super String>> m) {
    return jsonPath(
        "$.categories[?(@.name=='Test')].nodes[?(@.label=='" + label + "')].conclusion", m);
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
