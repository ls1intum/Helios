package de.tum.cit.aet.helios.workflow.pipeline;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end guard for {@code GET /api/pipeline/branch}. Covers both modes:
 *
 * <ul>
 *   <li>a canonical repository (in {@code helios.pipeline.repositories}) aggregates its jobs into
 *       the always-visible Build/Tests/Quality nodes (unmatched → PENDING);
 *   <li>a non-canonical repository falls back to its workflow-run view (never the Artemis-shaped
 *       catalog), so it isn't reduced to an all-pending skeleton;
 *   <li>no {@code X-REPOSITORY-ID} yields an empty pipeline (never a cross-repository leak).
 * </ul>
 */
class PipelineIT extends HeliosIntegrationTest {

  // Canonical repo: nameWithOwner must be in helios.pipeline.repositories (default: Artemis).
  private static final long REPO = 1L;
  private static final long WF = 51L;
  private static final long RUN = 61L;
  private static final String BRANCH = "main";
  private static final String SHA = "deadbeef";

  // Non-canonical repo -> group/run fallback.
  private static final long REPO_B = 2L;
  private static final long WF_B = 71L;
  private static final long RUN_B = 81L;
  private static final String BRANCH_B = "dev";
  private static final String SHA_B = "cafebabe";

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO, "ls1intum/Artemis");
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

    insertRepo(jdbc, REPO_B, "ls1intum/other");
    insertBranch(jdbc, REPO_B, BRANCH_B, SHA_B);
    insertWorkflow(jdbc, WF_B, REPO_B);
    insertRun(jdbc, RUN_B, REPO_B, WF_B, BRANCH_B, SHA_B);
  }

  @Test
  void canonicalRepositoryAggregatesJobsIntoCanonicalNodes() throws Exception {
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
        .andExpect(jsonPath("$.categories[2].nodes[1].status").value("PENDING"))
        // Gate is always present for a canonical repo; here no matching job → not running yet.
        .andExpect(jsonPath("$.gate.key").value("ci-gate"))
        .andExpect(jsonPath("$.gate.status").value("PENDING"));
  }

  @Test
  void nodeStates_queuedIsNotRunning_failFast_andGateReflectRequiredChecks() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long runC = 62L;
    final String branchC = "feature";
    final String shaC = "feedface";
    insertBranch(jdbc, REPO, branchC, shaC);
    insertRun(jdbc, runC, REPO, WF, branchC, shaC);
    // E2E: queued only → the node is "not running yet" (PENDING), not a spinner.
    insertJob(jdbc, 201, REPO, runC, "E2E / Phase 1", "QUEUED", null);
    // Native fail-fast + failing-leg link: leg 203 still runs and sorts first; leg 204 already
    // failed. The node is FAILURE and must link to 204, so a plain "first URL" (203) fails here.
    insertJob(jdbc, 203, REPO, runC, "Build / Build .war artifact", "IN_PROGRESS", null);
    insertJob(jdbc, 204, REPO, runC, "Build / Build .war artifact (retry)", "COMPLETED", "FAILURE");
    // The required-checks gate job failed.
    insertJob(jdbc, 205, REPO, runC, "All required CI Passed", "COMPLETED", "FAILURE");

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branchC)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        // E2E queued-only → not running yet.
        .andExpect(jsonPath("$.categories[1].nodes[2].key").value("test-e2e"))
        .andExpect(jsonPath("$.categories[1].nodes[2].status").value("PENDING"))
        .andExpect(jsonPath("$.categories[1].nodes[2].conclusion").doesNotExist())
        // Native fail-fast: FAILURE even though a leg is still IN_PROGRESS, and the link points at
        // the failing leg (204), not the still-running one (203) that sorts first.
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").value("FAILURE"))
        .andExpect(jsonPath("$.categories[0].nodes[0].htmlUrl").value("https://example/job/204"))
        // Gate reflects the required-checks job.
        .andExpect(jsonPath("$.gate.status").value("COMPLETED"))
        .andExpect(jsonPath("$.gate.conclusion").value("FAILURE"));
  }

  @Test
  void worstWinsConclusionPrecedenceAcrossLegs() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long runD = 63L;
    final String branchD = "release";
    final String shaD = "d00dfeed";
    insertBranch(jdbc, REPO, branchD, shaD);
    insertRun(jdbc, runD, REPO, WF, branchD, shaD);
    // quality-server aggregates style + quality; cancelled beats success.
    insertJob(jdbc, 301, REPO, runD, "Quality / Server Code Style", "COMPLETED", "SUCCESS");
    insertJob(jdbc, 302, REPO, runD, "Quality / Server Code Quality", "COMPLETED", "CANCELLED");
    // quality-client: every leg skipped → the node is skipped (not success).
    insertJob(jdbc, 303, REPO, runD, "Quality / Client Code Style", "COMPLETED", "SKIPPED");
    insertJob(jdbc, 304, REPO, runD, "Quality / Client Compilation", "COMPLETED", "SKIPPED");
    // build-docker: real Artemis shape — PR image ran, release-only leg skipped → success.
    insertJob(jdbc, 305, REPO, runD, "Build / Build and Push Docker Image (PR, amd64)", "COMPLETED",
        "SUCCESS");
    insertJob(jdbc, 306, REPO, runD, "Build / Build and Push Docker Image", "COMPLETED", "SKIPPED");
    // build-native: a terminal job with no pass/fail/skip verdict folds to NEUTRAL.
    insertJob(jdbc, 307, REPO, runD, "Build / Build .war artifact", "COMPLETED", "ACTION_REQUIRED");

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branchD)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        // CANCELLED > SUCCESS
        .andExpect(jsonPath("$.categories[2].nodes[1].key").value("quality-server"))
        .andExpect(jsonPath("$.categories[2].nodes[1].conclusion").value("CANCELLED"))
        // all-skipped → SKIPPED
        .andExpect(jsonPath("$.categories[2].nodes[0].key").value("quality-client"))
        .andExpect(jsonPath("$.categories[2].nodes[0].conclusion").value("SKIPPED"))
        // mixed success+skipped → SUCCESS (not skipped)
        .andExpect(jsonPath("$.categories[0].nodes[1].key").value("build-docker"))
        .andExpect(jsonPath("$.categories[0].nodes[1].conclusion").value("SUCCESS"))
        // action_required/stale/neutral fold to NEUTRAL (no warning state)
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").value("NEUTRAL"));
  }

  @Test
  void workflowNameMatcherExcludesJobsFromOtherWorkflows() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long runE = 64L;
    final String branchE = "hotfix";
    final String shaE = "b16b00b5";
    insertBranch(jdbc, REPO, branchE, shaE);
    insertRun(jdbc, runE, REPO, WF, branchE, shaE);
    // A job whose NAME matches test-client but that ran in a different workflow must not feed the
    // node — workflow-name-matcher "CI" scopes matching to the CI orchestrator run.
    insertJob(jdbc, 401, REPO, runE, "Test / Client Tests", "Nightly", "COMPLETED", "SUCCESS");

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branchE)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[1].nodes[0].key").value("test-client"))
        .andExpect(jsonPath("$.categories[1].nodes[0].status").value("PENDING"));
  }

  @Test
  void nonCanonicalRepositoryFallsBackToItsRunsNotCanonicalNodes() throws Exception {
    // ls1intum/other is not in the canonical allow-list, so the pipeline shows its workflow runs
    // (here ungrouped) rather than the Artemis Build/Tests/Quality catalog.
    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", BRANCH_B)
                .header(X_REPOSITORY_ID, String.valueOf(REPO_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(1))
        .andExpect(jsonPath("$.categories[0].name").value("Ungrouped"))
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("run-" + RUN_B))
        .andExpect(jsonPath("$.categories[0].nodes[0].label").value("CI"))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("COMPLETED"));
  }

  @Test
  void withoutRepositoryContextPipelineIsEmpty() throws Exception {
    // No X-REPOSITORY-ID → no repository → nothing to show (never leaks another repo's runs).
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
    insertJob(jdbc, id, repositoryId, runId, name, "CI", statusValue, conclusionValue);
  }

  private static void insertJob(
      JdbcTemplate jdbc,
      long id,
      long repositoryId,
      long runId,
      String name,
      String workflowName,
      String statusValue,
      String conclusionValue) {
    jdbc.update(
        "INSERT INTO workflow_job (id, repository_id, workflow_run_id, name, workflow_name, "
            + "status, conclusion, html_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        repositoryId,
        runId,
        name,
        workflowName,
        statusValue,
        conclusionValue,
        "https://example/job/" + id);
  }
}
