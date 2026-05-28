package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.deployment.github.DeploymentSource;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.environment.EnvironmentService.ReviewerResolution;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment.AutoApprovalDecision;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.user.User;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Decides what to do when GitHub fires a {@code deployment_status: waiting} event for a Helios-
 * originated deployment to a protected environment. Turns "GitHub is waiting on a required
 * reviewer" into either an immediate auto-approval (when the deployer is themselves a required
 * reviewer) or a deferral that surfaces the deployment in the in-app pending-approvals list.
 *
 * <p>The upstream startup-time gate on the webhook still applies (see
 * {@code GitHubDeploymentStatusMessageHandler}), so old events aren't re-processed on restart.
 * On top of that, this service is itself idempotent against webhook redelivery: once
 * {@link HeliosDeployment#getAutoApprovalDecision()} is non-null, a second invocation is a no-op.
 *
 * <p>Atomicity: the audit-row save and the deployment-stamp save live in a single transaction via
 * {@link ApprovalDecisionWriter}, so a crash between the two writes cannot leave the database in
 * a half-state. The bounded retry against the persist-vs-webhook race runs *outside* that
 * transaction so each lookup reads a fresh snapshot.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ApprovalService {

  /**
   * Total attempts to find the {@link HeliosDeployment} row when the webhook arrives just before
   * the deploy-side transaction commits. Three attempts with the delays below close the race
   * window for any realistic dispatch latency without blocking the NATS dispatcher for long.
   */
  private static final int FIND_DEPLOYMENT_MAX_ATTEMPTS = 3;

  private static final long[] FIND_DEPLOYMENT_RETRY_DELAYS_MS = {100L, 300L};

  private final GitHubService gitHubService;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final EnvironmentService environmentService;
  private final ApprovalDecisionWriter decisionWriter;

  public void reviewDeployment(
      DeploymentSource deploymentSource,
      GitRepository gitRepository,
      Environment environment,
      User user) {

    HeliosDeployment heliosDeployment =
        findHeliosDeploymentWithRetry(deploymentSource.getId()).orElse(null);
    if (heliosDeployment == null) {
      // The deployment_status webhook arrived for something Helios doesn't know about — most
      // likely a deployment that wasn't dispatched from Helios. Leave it for GitHub's normal flow.
      log.info("Skipping approval for non-Helios deployment with ID {}", deploymentSource.getId());
      return;
    }

    // Idempotency: GitHub redelivers webhooks; we treat the first decision as authoritative.
    AutoApprovalDecision priorDecision = heliosDeployment.getAutoApprovalDecision();
    if (priorDecision != null) {
      log.info(
          "Skipping reviewDeployment for {}; already decided as {}.",
          heliosDeployment.getId(),
          priorDecision);
      return;
    }

    Long workflowRunId = heliosDeployment.getWorkflowRunId();
    if (workflowRunId == null) {
      // Helios dispatch raced this webhook even after the retry, or the workflowRunId update
      // failed. Either way: leave the deployment in WAITING and don't act — we have no run id to
      // call the GitHub API with.
      log.warn(
          "Missing workflow run ID for Helios deployment {}; cannot resolve approval.",
          heliosDeployment.getId());
      return;
    }

    Optional<ReviewerResolution> reviewersOpt =
        environmentService.resolveReviewers(environment.getId());
    if (reviewersOpt.isEmpty()) {
      // No required-reviewer rule on this env. GitHub may still be gating on a wait-timer or
      // branch policy, which it resolves on its own — nothing for Helios to do.
      log.info(
          "No required-reviewer rule for environment {}; leaving deployment {} to GitHub.",
          environment.getName(),
          heliosDeployment.getId());
      decisionWriter.recordDecisionOnly(heliosDeployment, AutoApprovalDecision.NOT_APPLICABLE);
      return;
    }

    ReviewerResolution reviewers = reviewersOpt.get();
    String creatorLogin =
        heliosDeployment.getCreator() != null ? heliosDeployment.getCreator().getLogin() : null;
    String repoNameWithOwner = gitRepository.getNameWithOwner();

    // Team-type reviewers are not yet expanded to members. Fall back to today's behavior
    // (impersonate creator, try anyway) rather than silently no-opping, which would regress
    // working setups where the creator is a team member.
    if (reviewers.hasTeamReviewers()) {
      log.info(
          "Environment {} has team-type required reviewers; attempting legacy auto-approve as "
              + "deployment creator @{}. Helios does not yet expand teams to members.",
          environment.getName(),
          creatorLogin);
      attemptAutoApprove(
          heliosDeployment,
          repoNameWithOwner,
          workflowRunId,
          environment.getId(),
          creatorLogin,
          "Auto-approved by Helios (deployer is required reviewer, team-fallback path)",
          AutoApprovalDecision.TEAM_REVIEWER_FALLBACK);
      return;
    }

    // Pure User-type rule. Self-review prevention overrides everything: if the rule prevents the
    // user who triggered the deploy from approving it, we cannot auto-approve no matter who they
    // are.
    if (reviewers.preventSelfReview() || creatorLogin == null
        || !reviewers.userLogins().contains(creatorLogin)) {
      String reason =
          reviewers.preventSelfReview()
              ? "preventSelfReview is on"
              : (creatorLogin == null
                  ? "no creator login recorded"
                  : "creator is not in required-reviewer list");
      log.info(
          "Deferring deployment {} ({}): {} required reviewers will be notified [{}].",
          heliosDeployment.getId(),
          reason,
          reviewers.userLogins().size(),
          String.join(", ", reviewers.userLogins()));
      decisionWriter.recordDecisionOnly(
          heliosDeployment, AutoApprovalDecision.DEFERRED_TO_REVIEWERS);
      // Phase 2 surfaces these in the in-app pending-approvals list (and Phase 3 emails). For
      // now the deployment stays WAITING on GitHub until a reviewer acts in Helios.
      return;
    }

    // Happy path: the deployer is one of the required reviewers and self-review is allowed.
    attemptAutoApprove(
        heliosDeployment,
        repoNameWithOwner,
        workflowRunId,
        environment.getId(),
        creatorLogin,
        "Auto-approved by Helios (deployer is required reviewer)",
        AutoApprovalDecision.AUTO_APPROVED);
  }

  private void attemptAutoApprove(
      HeliosDeployment heliosDeployment,
      String repoNameWithOwner,
      long workflowRunId,
      Long environmentId,
      String impersonatedLogin,
      String comment,
      AutoApprovalDecision decisionOnSuccess) {

    DeploymentApprovalRequest auditRow =
        newAutoApprovalRow(heliosDeployment, impersonatedLogin);

    try {
      gitHubService.approveDeploymentOnBehalfOfUser(
          repoNameWithOwner, workflowRunId, environmentId, impersonatedLogin, comment);

      decisionWriter.recordSuccess(heliosDeployment, auditRow, decisionOnSuccess);
      log.info(
          "Auto-approved deployment {} on behalf of @{} ({}).",
          heliosDeployment.getId(),
          impersonatedLogin,
          decisionOnSuccess);
    } catch (IOException e) {
      // Most likely "creator is not actually authorised on this env" (legacy team-fallback) or a
      // transient GitHub failure. Persist the audit row so the failure is visible and the row
      // can be retried by Phase-2 in-app approval (a reviewer can still resolve it manually).
      AutoApprovalDecision fallback =
          decisionOnSuccess == AutoApprovalDecision.TEAM_REVIEWER_FALLBACK
              ? AutoApprovalDecision.TEAM_REVIEWER_FALLBACK
              : AutoApprovalDecision.DEFERRED_TO_REVIEWERS;
      decisionWriter.recordFailure(heliosDeployment, auditRow, fallback, e.getMessage());
      log.warn(
          "Auto-approve failed for deployment {} (impersonating @{}): {}. "
              + "Falling back to deferred approval.",
          heliosDeployment.getId(),
          impersonatedLogin,
          e.getMessage());
    }
  }

  private DeploymentApprovalRequest newAutoApprovalRow(
      HeliosDeployment heliosDeployment, String impersonatedLogin) {
    DeploymentApprovalRequest row = new DeploymentApprovalRequest();
    row.setHeliosDeployment(heliosDeployment);
    row.setReviewer(heliosDeployment.getCreator());
    row.setReviewerLogin(impersonatedLogin != null ? impersonatedLogin : "");
    row.setVia(DeploymentApprovalRequest.Via.AUTO);
    row.setState(DeploymentApprovalRequest.State.PENDING);
    // AUTO rows have no token TTL semantics; expiresAt is left null (column is nullable).
    return row;
  }

  /**
   * Bounded retry against the race between {@code DeploymentService.deployToEnvironment}'s commit
   * and the webhook arrival. Each attempt is its own (implicit) transaction so it reads a fresh
   * snapshot; if all attempts miss we treat the deployment as non-Helios.
   */
  private Optional<HeliosDeployment> findHeliosDeploymentWithRetry(Long deploymentId) {
    Optional<HeliosDeployment> found = heliosDeploymentRepository.findByDeploymentId(deploymentId);
    if (found.isPresent()) {
      return found;
    }
    for (int delayIdx = 0;
        delayIdx < FIND_DEPLOYMENT_RETRY_DELAYS_MS.length
            && delayIdx < FIND_DEPLOYMENT_MAX_ATTEMPTS - 1;
        delayIdx++) {
      try {
        Thread.sleep(FIND_DEPLOYMENT_RETRY_DELAYS_MS[delayIdx]);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return Optional.empty();
      }
      found = heliosDeploymentRepository.findByDeploymentId(deploymentId);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

}
