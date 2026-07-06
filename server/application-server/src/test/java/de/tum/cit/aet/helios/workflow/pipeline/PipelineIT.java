package de.tum.cit.aet.helios.workflow.pipeline;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end guard for {@code GET /api/pipeline/branch}. With no persisted config, a repo's
 * pipeline is auto-detected from its observed CI jobs into the Build/Test/Quality lanes, each node
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
        // No required-checks job seeded → the gate badge is hidden (not a permanent PENDING badge).
        .andExpect(jsonPath("$.gate").doesNotExist());
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
  void aggregatesWorstWinsAndInProgressWhenNoLegFailed() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long run = 64L;
    final String branch = "aggregate";
    final String sha = "abadcafe";
    insertBranch(jdbc, REPO, branch, sha);
    insertRun(jdbc, run, REPO, WF, branch, sha);
    // Build "Deploy artifact": a passing leg + a cancelled leg (matrix legs collapse to one node)
    // → worst-wins is CANCELLED (neutral), and crucially not a failure.
    insertJob(jdbc, 401, REPO, run, "Build / Deploy artifact", "COMPLETED", "SUCCESS");
    insertJob(jdbc, 402, REPO, run, "Build / Deploy artifact (2)", "COMPLETED", "CANCELLED");
    // Test "Server Tests": a passing leg + a still-running leg → IN_PROGRESS (not fail, not done).
    insertJob(jdbc, 403, REPO, run, "Test / Server Tests", "COMPLETED", "SUCCESS");
    insertJob(jdbc, 404, REPO, run, "Test / Server Tests (2)", "IN_PROGRESS", null);
    // Quality "Lint": a single skipped leg → SKIPPED.
    insertJob(jdbc, 405, REPO, run, "Quality / Lint", "COMPLETED", "SKIPPED");

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(buildNodeStatus("Deploy artifact", hasItem("COMPLETED")))
        .andExpect(buildNodeConclusion("Deploy artifact", hasItem("CANCELLED")))
        // A still-running leg keeps the node IN_PROGRESS since nothing failed.
        .andExpect(testNodeStatus("Server Tests", hasItem("IN_PROGRESS")))
        .andExpect(
            jsonPath(
                "$.categories[?(@.name=='Quality')].nodes[?(@.label=='Lint')].conclusion",
                hasItem("SKIPPED")));
  }

  @Test
  void gateShowsSuccessWhenRequiredChecksJobPasses() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long run = 65L;
    final String branch = "gate-green";
    final String sha = "600df00d";
    insertBranch(jdbc, REPO, branch, sha);
    insertRun(jdbc, run, REPO, WF, branch, sha);
    insertJob(jdbc, 501, REPO, run, "All required CI Passed", "COMPLETED", "SUCCESS");

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gate.key").value("ci-gate"))
        .andExpect(jsonPath("$.gate.status").value("COMPLETED"))
        .andExpect(jsonPath("$.gate.conclusion").value("SUCCESS"));
  }

  @Test
  void pipelineDoesNotLeakAnotherRepositorysJobs() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long repoB = 2L;
    final long wfB = 52L;
    final long runB = 66L;
    insertRepo(jdbc, repoB, "ls1intum/repo-b");
    // Repo B shares the branch name and has an identically-named Build job — but it FAILED.
    insertBranch(jdbc, repoB, BRANCH, SHA);
    insertWorkflow(jdbc, wfB, repoB);
    insertRun(jdbc, runB, repoB, wfB, BRANCH, SHA);
    insertJob(jdbc, 601, repoB, runB, "Build / Build .war artifact", "COMPLETED", "FAILURE");

    // Repo A's pipeline reflects only A's job (SUCCESS), never B's identically-named FAILURE.
    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", BRANCH)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(buildNodeConclusion("Build .war artifact", hasItem("SUCCESS")))
        .andExpect(buildNodeConclusion("Build .war artifact", not(hasItem("FAILURE"))));
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
    return jsonPath(
        "$.categories[?(@.name=='Build')].nodes[?(@.label=='" + label + "')].status", m);
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
