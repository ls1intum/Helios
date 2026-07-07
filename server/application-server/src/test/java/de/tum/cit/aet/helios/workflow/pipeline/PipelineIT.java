package de.tum.cit.aet.helios.workflow.pipeline;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end guard for {@code GET /api/pipeline/branch} and {@code /api/pipeline/pr/{id}}. Covers
 * both modes:
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
    // Run still in progress: nodes with a matching job show their job state; nodes with no job yet
    // are inferred from the run ("queued"), not left as a dead "not running yet".
    insertRun(jdbc, RUN, REPO, WF, BRANCH, SHA, "IN_PROGRESS", null, 0);
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
        // e2e has no job yet, but the CI run is in progress → mirror the run as IN_PROGRESS (a
        // running pipeline reads as running), not a dead "pending".
        .andExpect(jsonPath("$.categories[1].nodes[2].key").value("test-e2e"))
        .andExpect(jsonPath("$.categories[1].nodes[2].status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.categories[1].nodes[2].conclusion").doesNotExist())
        // Quality: client matched, server has no job yet but the run is active → in progress.
        .andExpect(jsonPath("$.categories[2].name").value("Quality"))
        .andExpect(jsonPath("$.categories[2].nodes[0].key").value("quality-client"))
        .andExpect(jsonPath("$.categories[2].nodes[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.categories[2].nodes[1].key").value("quality-server"))
        .andExpect(jsonPath("$.categories[2].nodes[1].status").value("IN_PROGRESS"))
        // Gate is always present for a canonical repo; no job yet but run active → in progress.
        .andExpect(jsonPath("$.gate.key").value("ci-gate"))
        .andExpect(jsonPath("$.gate.status").value("IN_PROGRESS"))
        // Freshness anchor: these states reflect the branch head commit (up to date).
        .andExpect(jsonPath("$.head.sha").value("deadbee"))
        .andExpect(jsonPath("$.head.upToDate").value(true))
        // The freshness anchor's SHA links to the commit on GitHub (not dead text).
        .andExpect(
            jsonPath("$.head.htmlUrl")
                .value("https://github.com/ls1intum/Artemis/commit/deadbeef"));
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
        // E2E queued-only → "queued" (scheduled), and crucially not a running spinner.
        .andExpect(jsonPath("$.categories[1].nodes[2].key").value("test-e2e"))
        .andExpect(jsonPath("$.categories[1].nodes[2].status").value("QUEUED"))
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
        // The Nightly job is excluded, so test-client is not fed by it: the CI run completed with
        // no matching job → the honest NEUTRAL ("no result"), never the Nightly job's SUCCESS.
        .andExpect(jsonPath("$.categories[1].nodes[0].key").value("test-client"))
        .andExpect(jsonPath("$.categories[1].nodes[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.categories[1].nodes[0].conclusion").value("NEUTRAL"));
  }

  @Test
  void queuedRunFillsEveryNodeAsQueuedNotPending() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "queued-br";
    final String sha = "0000aaaa";
    insertBranch(jdbc, REPO, branch, sha);
    // A freshly-pushed commit: the CI run is queued and has produced no jobs yet.
    insertRun(jdbc, 65L, REPO, WF, branch, sha, "QUEUED", null, 0);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        // No jobs, but the run is queued → every node is "queued", never a dead "not running yet".
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("QUEUED"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").doesNotExist())
        .andExpect(jsonPath("$.gate.status").value("QUEUED"))
        .andExpect(jsonPath("$.head.sha").value("0000aaa"))
        .andExpect(jsonPath("$.head.upToDate").value(true));
  }

  @Test
  void inProgressRunShowsJoblessNodesAsRunningNotQueued() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "running-br";
    final String sha = "abcd1234";
    insertBranch(jdbc, REPO, branch, sha);
    // The run is executing, but this stage's job hasn't been ingested yet (common: job events lag
    // the run on a busy CI). The node must mirror the running run, not under-state it as queued.
    insertRun(jdbc, 90L, REPO, WF, branch, sha, "IN_PROGRESS", null, 0);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").doesNotExist())
        .andExpect(jsonPath("$.gate.status").value("IN_PROGRESS"));
  }

  @Test
  void runAwaitingApprovalShowsWaitingForApproval() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "gated-br";
    final String sha = "1111bbbb";
    insertBranch(jdbc, REPO, branch, sha);
    // Gated run (e.g. first-time contributor): awaiting maintainer approval, no jobs created.
    insertRun(jdbc, 66L, REPO, WF, branch, sha, "ACTION_REQUIRED", null, 0);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        // Actionable state surfaced instead of "not running yet".
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("WAITING"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").value("ACTION_REQUIRED"))
        .andExpect(jsonPath("$.gate.conclusion").value("ACTION_REQUIRED"));
  }

  @Test
  void headWithoutRunsFallsBackToLatestCommitThatRan() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "fallback-br";
    // The branch head has no CI run yet (just pushed / docs-only / missed push webhook)...
    insertBranch(jdbc, REPO, branch, "9999head");
    // ...but an earlier commit did run and passed.
    insertRun(jdbc, 67L, REPO, WF, branch, "8888prev", "COMPLETED", "SUCCESS", 60);
    insertJob(jdbc, 501, REPO, 67L, "Build / Build .war artifact", "COMPLETED", "SUCCESS");

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        // Shows the commit that actually ran, clearly flagged as not the head.
        .andExpect(jsonPath("$.head.sha").value("8888pre"))
        .andExpect(jsonPath("$.head.upToDate").value(false))
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").value("SUCCESS"));
  }

  @Test
  void previousCommitOutcomeShownWhileHeadStillRunning() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "prevfoot-br";
    insertBranch(jdbc, REPO, branch, "aaaahead");
    // Head commit is still building...
    insertRun(jdbc, 68L, REPO, WF, branch, "aaaahead", "IN_PROGRESS", null, 0);
    insertJob(jdbc, 601, REPO, 68L, "Build / Build .war artifact", "IN_PROGRESS", null);
    // ...the previous commit finished and passed.
    insertRun(jdbc, 69L, REPO, WF, branch, "bbbbprev", "COMPLETED", "SUCCESS", 120);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.head.sha").value("aaaahea"))
        .andExpect(jsonPath("$.head.upToDate").value(true))
        // Confidence footer: the previous commit's outcome, shown while the head is still running.
        .andExpect(jsonPath("$.previous.sha").value("bbbbpre"))
        .andExpect(jsonPath("$.previous.conclusion").value("SUCCESS"))
        // The footer's SHA links to that commit's CI run.
        .andExpect(jsonPath("$.previous.htmlUrl").value("https://example/run/69"));
  }

  @Test
  void previousFooterWalksPastCancelledToLastDefinitiveResult() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "walkback-br";
    insertBranch(jdbc, REPO, branch, "aaaahead");
    // Head still building.
    insertRun(jdbc, 200L, REPO, WF, branch, "aaaahead", "IN_PROGRESS", null, 0);
    insertJob(jdbc, 620, REPO, 200L, "Build / Build .war artifact", "IN_PROGRESS", null);
    // The immediately-preceding commit was cancelled (superseded) — no confidence signal...
    insertRun(jdbc, 201L, REPO, WF, branch, "ccccanc", "COMPLETED", "CANCELLED", 60);
    // ...so the footer walks further back to the last commit that actually passed/failed.
    insertRun(jdbc, 202L, REPO, WF, branch, "ddddok", "COMPLETED", "SUCCESS", 120);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previous.sha").value("ddddok"))
        .andExpect(jsonPath("$.previous.conclusion").value("SUCCESS"))
        .andExpect(jsonPath("$.previous.htmlUrl").value("https://example/run/202"));
  }

  @Test
  void previousFooterLinksTheCiRunNotACoRunningWorkflow() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "ci-scope-br";
    insertBranch(jdbc, REPO, branch, "aaaahead");
    insertRun(jdbc, 300L, REPO, WF, branch, "aaaahead", "IN_PROGRESS", null, 0);
    insertJob(jdbc, 640, REPO, 300L, "Build / Build .war artifact", "IN_PROGRESS", null);
    // Previous commit: the CI run passed, and a *separate* workflow (e.g. Code Quality) also ran on
    // the same commit. The footer must reflect and link the CI run — never the other workflow.
    insertRun(jdbc, 301L, REPO, WF, branch, "eeeeprev", "COMPLETED", "SUCCESS", 60);
    insertWorkflow(jdbc, 52L, REPO);
    insertNamedRun(
        jdbc, 302L, REPO, 52L, branch, "eeeeprev", "Code Quality", "COMPLETED", "SUCCESS", 60);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previous.sha").value("eeeepre"))
        .andExpect(jsonPath("$.previous.conclusion").value("SUCCESS"))
        // The CI run (301), never the Code Quality run (302), regardless of run ordering.
        .andExpect(jsonPath("$.previous.htmlUrl").value("https://example/run/301"));
  }

  @Test
  void previousFooterHiddenWhenNoDefiniteResultInHistory() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "nodef-br";
    insertBranch(jdbc, REPO, branch, "aaaahead");
    insertRun(jdbc, 210L, REPO, WF, branch, "aaaahead", "IN_PROGRESS", null, 0);
    insertJob(jdbc, 621, REPO, 210L, "Build / Build .war artifact", "IN_PROGRESS", null);
    // Only a cancelled commit behind the head: no pass/fail to show, so no footer at all.
    insertRun(jdbc, 211L, REPO, WF, branch, "ccccanc", "COMPLETED", "CANCELLED", 60);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previous").doesNotExist());
  }

  @Test
  void completedHeadHasNoPreviousFooter() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "done-br";
    insertBranch(jdbc, REPO, branch, "ccccdone");
    insertRun(jdbc, 70L, REPO, WF, branch, "ccccdone", "COMPLETED", "SUCCESS", 0);
    insertJob(jdbc, 701, REPO, 70L, "Build / Build .war artifact", "COMPLETED", "SUCCESS");
    // An earlier commit exists, but once the head is terminal its own row tells the whole story.
    insertRun(jdbc, 71L, REPO, WF, branch, "eeeeold", "COMPLETED", "FAILURE", 300);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.head.upToDate").value(true))
        .andExpect(jsonPath("$.previous").doesNotExist());
  }

  @Test
  void rerunReplacesFailedAttemptWithLatestGreenAttempt() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long runR = 72L;
    final String branch = "rerun-br";
    final String sha = "abcdef01";
    insertBranch(jdbc, REPO, branch, sha);
    insertRun(jdbc, runR, REPO, WF, branch, sha, "COMPLETED", "SUCCESS", 0);
    // Attempt 1 of the build job failed; the developer re-ran and attempt 2 (higher job id) passed.
    // GitHub keeps both rows under the same run; only the latest attempt must count, else the node
    // is red forever after a green re-run.
    insertJob(jdbc, 801, REPO, runR, "Build / Build .war artifact", "COMPLETED", "FAILURE");
    insertJob(jdbc, 802, REPO, runR, "Build / Build .war artifact", "COMPLETED", "SUCCESS");

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").value("SUCCESS"));
  }

  @Test
  void branchWithNoRunsShowsPendingSkeletonNotFallback() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "no-ci-br";
    // A branch that has never triggered CI: legitimately "not running yet", not a stale fallback.
    insertBranch(jdbc, REPO, branch, "5555none");

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("PENDING"))
        .andExpect(jsonPath("$.head.sha").value("5555non"))
        .andExpect(jsonPath("$.head.upToDate").value(true))
        .andExpect(jsonPath("$.previous").doesNotExist());
  }

  @Test
  void unrelatedWorkflowRunLeavesNodesPending() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "nightly-br";
    final String sha = "6666nite";
    insertBranch(jdbc, REPO, branch, sha);
    // The only run on this commit is an unrelated "Nightly" workflow (not the CI orchestrator). It
    // must not fill the canonical nodes as "queued"; they stay PENDING (their CI run is absent).
    insertNamedRun(jdbc, 73L, REPO, WF, branch, sha, "Nightly", "COMPLETED", null, 0);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("PENDING"));
  }

  @Test
  void completedRunWithActionRequiredConclusionShowsWaitingForApproval() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "gated2-br";
    final String sha = "7777gate";
    insertBranch(jdbc, REPO, branch, sha);
    // A completed run whose conclusion is action_required (gate resolved to "needs approval"),
    // with no jobs → still surfaced as waiting-for-approval, not "no result".
    insertRun(jdbc, 74L, REPO, WF, branch, sha, "COMPLETED", "ACTION_REQUIRED", 0);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("WAITING"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").value("ACTION_REQUIRED"));
  }

  @Test
  void previousCommitFailureShownWhileHeadStillRunning() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "prevfail-br";
    insertBranch(jdbc, REPO, branch, "8888head");
    insertRun(jdbc, 75L, REPO, WF, branch, "8888head", "IN_PROGRESS", null, 0);
    insertJob(jdbc, 901, REPO, 75L, "Build / Build .war artifact", "IN_PROGRESS", null);
    // The previous commit finished and FAILED — worst-wins across its runs.
    insertRun(jdbc, 76L, REPO, WF, branch, "7777prev", "COMPLETED", "FAILURE", 120);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previous.sha").value("7777pre"))
        .andExpect(jsonPath("$.previous.conclusion").value("FAILURE"));
  }

  @Test
  void previousCommitStillRunningHidesFooter() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final String branch = "prevrun-br";
    insertBranch(jdbc, REPO, branch, "aaaanew");
    insertRun(jdbc, 77L, REPO, WF, branch, "aaaanew", "IN_PROGRESS", null, 0);
    insertJob(jdbc, 910, REPO, 77L, "Build / Build .war artifact", "IN_PROGRESS", null);
    // The previous commit is itself still running → no confident outcome → footer hidden.
    insertRun(jdbc, 78L, REPO, WF, branch, "bbbbrun", "IN_PROGRESS", null, 120);

    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", branch)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previous").doesNotExist());
  }

  @Test
  void pullRequestPipelineResolvesFromAssociatedRuns() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long prId = 5000L;
    final long runP = 79L;
    final String prSha = "9999prsh";
    insertPullRequest(jdbc, prId, REPO, "pr-head", prSha);
    insertRun(jdbc, runP, REPO, WF, "pr-head", prSha, "IN_PROGRESS", null, 0);
    // Association carries the run into the PR's universe (mirrors the workflow_run <-> PR join).
    jdbc.update(
        "INSERT INTO workflow_run_pull_requests (pull_requests_id, workflow_run_id) VALUES (?, ?)",
        prId,
        runP);
    insertJob(jdbc, 950, REPO, runP, "Build / Build .war artifact", "COMPLETED", "SUCCESS");

    mockMvc
        .perform(
            get("/api/pipeline/pr/{pullRequestId}", prId)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("build-native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").value("SUCCESS"))
        .andExpect(jsonPath("$.head.sha").value("9999prs"))
        .andExpect(jsonPath("$.head.upToDate").value(true));
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
    insertRun(jdbc, id, repositoryId, workflowId, branch, sha, "COMPLETED", null, 0);
  }

  /** {@code ageSeconds} pushes {@code created_at} back so commit ordering is deterministic. */
  private static void insertRun(
      JdbcTemplate jdbc,
      long id,
      long repositoryId,
      long workflowId,
      String branch,
      String sha,
      String statusValue,
      String conclusionValue,
      int ageSeconds) {
    insertNamedRun(
        jdbc, id, repositoryId, workflowId, branch, sha, "CI", statusValue, conclusionValue,
        ageSeconds);
  }

  /** As {@link #insertRun}, but with an explicit workflow {@code name} (e.g. a non-CI workflow). */
  private static void insertNamedRun(
      JdbcTemplate jdbc,
      long id,
      long repositoryId,
      long workflowId,
      String branch,
      String sha,
      String name,
      String statusValue,
      String conclusionValue,
      int ageSeconds) {
    jdbc.update(
        "INSERT INTO workflow_run (id, repository_id, workflow_id, run_attempt, run_number, name, "
            + "status, conclusion, head_branch, head_sha, html_url, created_at, updated_at) "
            + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, now() - (? * interval '1 second'), now())",
        id,
        repositoryId,
        workflowId,
        id,
        name,
        statusValue,
        conclusionValue,
        branch,
        sha,
        "https://example/run/" + id,
        ageSeconds);
  }

  /** Seeds a pull request (a discriminated {@code issue} row) for the PR pipeline endpoint. */
  private static void insertPullRequest(
      JdbcTemplate jdbc, long id, long repositoryId, String headRefName, String headSha) {
    jdbc.update(
        "INSERT INTO issue (id, repository_id, issue_type, number, comments_count, is_locked, "
            + "is_draft, is_merged, maintainer_can_modify, additions, deletions, commits, "
            + "changed_files, state, title, html_url, head_ref_name, head_sha) "
            + "VALUES (?, ?, 'PULL_REQUEST', ?, 0, false, false, false, false, 0, 0, 0, 0, 'OPEN', "
            + "'PR', 'https://example/pr', ?, ?)",
        id,
        repositoryId,
        (int) id,
        headRefName,
        headSha);
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
