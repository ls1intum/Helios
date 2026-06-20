package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.environment.EnvironmentService.ReviewerResolution;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.user.User;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles a logged-in Helios user explicitly approving or declining a deployment that's waiting on
 * required reviewers. Companion to {@link ApprovalService}, which handles the webhook-driven
 * auto-approval path; this service exists for the in-app pending-approvals UI introduced in
 * Phase 2 (and will back the email-link endpoint in Phase 3).
 *
 * <p>Concurrency: the deployment row is pessimistically locked for the duration of the GitHub call
 * so two reviewers clicking Approve at the same instant cannot both succeed. The loser sees a 409
 * Conflict; the UI then refreshes to show "already handled".
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class DeploymentReviewActionService {

  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final DeploymentApprovalRequestRepository approvalRequestRepository;
  private final EnvironmentService environmentService;
  private final GitHubService gitHubService;

  /**
   * The {@code noRollbackFor = ResponseStatusException.class} is load-bearing: when GitHub
   * rejects an approval we throw {@link ResponseStatusException} with {@code BAD_GATEWAY} *after*
   * persisting a {@code FAILED_AT_GITHUB} audit row, and Spring's default behaviour is to roll
   * back the transaction on any {@link RuntimeException}. Without this annotation the audit row
   * is silently discarded and the UI cannot offer a meaningful retry path.
   */
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public DeploymentApprovalRequest approveAsCurrentUser(Long heliosDeploymentId, User currentUser) {
    return review(heliosDeploymentId, currentUser, true, null);
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public DeploymentApprovalRequest declineAsCurrentUser(
      Long heliosDeploymentId, User currentUser, String comment) {
    return review(heliosDeploymentId, currentUser, false, comment);
  }

  /**
   * Validates the caller is an eligible required reviewer, then approves or declines the deployment
   * on GitHub on their behalf and records the audit outcome.
   *
   * @param approve {@code true} for approval, {@code false} for decline
   * @param userComment optional free-form comment from the reviewer (decline path mostly)
   */
  private DeploymentApprovalRequest review(
      Long heliosDeploymentId, User currentUser, boolean approve, String userComment) {

    if (currentUser == null || currentUser.getLogin() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated user.");
    }
    String currentLogin = currentUser.getLogin();

    HeliosDeployment deployment =
        heliosDeploymentRepository
            .findByIdForUpdate(heliosDeploymentId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Deployment not found: " + heliosDeploymentId));

    Long workflowRunId = deployment.getWorkflowRunId();
    if (workflowRunId == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Deployment is not yet linked to a GitHub workflow run.");
    }

    List<DeploymentApprovalRequest> existing =
        approvalRequestRepository.findByHeliosDeployment(deployment);
    if (existing.stream().anyMatch(DeploymentReviewActionService::isTerminallyResolved)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This deployment has already been resolved.");
    }

    Environment environment = deployment.getEnvironment();
    ReviewerResolution reviewers =
        environmentService
            .resolveReviewers(environment.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This environment has no required-reviewer rule; nothing to approve."));

    // The caller may act if they are an explicit User-type required reviewer, or if they already
    // own an approval-request row for this deployment. The row-owner case is the recovery path
    // for the team-reviewer fallback: when an env has only Team-type reviewers, userLogins() is
    // empty, so the auto-approve path impersonated the creator and, on a GitHub rejection (e.g.
    // a transient 5xx), left them a FAILED_AT_GITHUB row they could not otherwise retry in-app.
    // GitHub stays the authority (it re-checks team membership and 502s an unauthorized retry),
    // so a row owner retrying cannot bypass it. Full team-member expansion is Phase-4.
    boolean ownsExistingRequest =
        existing.stream().anyMatch(r -> currentLogin.equals(r.getReviewerLogin()));
    if (!reviewers.userLogins().contains(currentLogin) && !ownsExistingRequest) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You are not a required reviewer for this environment.");
    }
    if (reviewers.preventSelfReview()
        && deployment.getCreator() != null
        && currentLogin.equals(deployment.getCreator().getLogin())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Self-review is prevented for this environment.");
    }

    String repoNameWithOwner = environment.getRepository().getNameWithOwner();
    String comment = buildAuditComment(currentLogin, approve, userComment);

    DeploymentApprovalRequest reviewerRow = findOrCreateForReviewer(deployment, currentUser);

    try {
      if (approve) {
        gitHubService.approveDeploymentOnBehalfOfUser(
            repoNameWithOwner, workflowRunId, environment.getId(), currentLogin, comment);
      } else {
        gitHubService.rejectDeploymentOnBehalfOfUser(
            repoNameWithOwner, workflowRunId, environment.getId(), currentLogin, comment);
      }
    } catch (IOException e) {
      reviewerRow.setState(DeploymentApprovalRequest.State.FAILED_AT_GITHUB);
      reviewerRow.setVia(DeploymentApprovalRequest.Via.IN_APP);
      reviewerRow.setRespondedAt(OffsetDateTime.now());
      reviewerRow.setFailureReason(e.getMessage());
      approvalRequestRepository.save(reviewerRow);
      log.warn(
          "In-app {} by @{} for deployment {} failed at GitHub: {}",
          approve ? "approve" : "decline",
          currentLogin,
          heliosDeploymentId,
          e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "GitHub rejected the request: " + e.getMessage(), e);
    }

    // GitHub accepted — finalise this reviewer's row and consume siblings.
    reviewerRow.setState(
        approve
            ? DeploymentApprovalRequest.State.APPROVED
            : DeploymentApprovalRequest.State.DECLINED);
    reviewerRow.setVia(DeploymentApprovalRequest.Via.IN_APP);
    reviewerRow.setRespondedAt(OffsetDateTime.now());
    approvalRequestRepository.save(reviewerRow);

    consumeSiblingPendingRows(existing, reviewerRow.getId());

    // Stamp the deployment so a no-op auto-approval path won't double-act if any later webhook
    // arrives for the same deployment.
    deployment.setAutoApprovalDecision(
        HeliosDeployment.AutoApprovalDecision.DEFERRED_TO_REVIEWERS);
    if (deployment.getAutoApprovalAt() == null) {
      deployment.setAutoApprovalAt(OffsetDateTime.now());
    }
    heliosDeploymentRepository.save(deployment);

    log.info(
        "Deployment {} {} by @{} via Helios (in-app).",
        heliosDeploymentId,
        approve ? "approved" : "declined",
        currentLogin);
    return reviewerRow;
  }

  private DeploymentApprovalRequest findOrCreateForReviewer(
      HeliosDeployment deployment, User reviewer) {
    return approvalRequestRepository
        .lockByDeploymentAndReviewerLogin(deployment.getId(), reviewer.getLogin())
        .orElseGet(
            () -> {
              DeploymentApprovalRequest row = new DeploymentApprovalRequest();
              row.setHeliosDeployment(deployment);
              row.setReviewer(reviewer);
              row.setReviewerLogin(reviewer.getLogin());
              row.setState(DeploymentApprovalRequest.State.PENDING);
              // expiresAt stays null — no TTL semantics for in-app clicks.
              return row;
            });
  }

  private void consumeSiblingPendingRows(
      List<DeploymentApprovalRequest> existingRows, Long winningRowId) {
    for (DeploymentApprovalRequest sibling : existingRows) {
      if (sibling.getId() != null && sibling.getId().equals(winningRowId)) {
        continue;
      }
      if (sibling.getState() == DeploymentApprovalRequest.State.PENDING) {
        sibling.setState(DeploymentApprovalRequest.State.CONSUMED_BY_OTHER);
        sibling.setRespondedAt(OffsetDateTime.now());
        approvalRequestRepository.save(sibling);
      }
    }
  }

  private static boolean isTerminallyResolved(DeploymentApprovalRequest r) {
    return r.getState() == DeploymentApprovalRequest.State.APPROVED
        || r.getState() == DeploymentApprovalRequest.State.DECLINED;
  }

  private static String buildAuditComment(String login, boolean approve, String userComment) {
    String base =
        (approve ? "Approved by @" : "Declined by @") + login + " via Helios (in-app)";
    if (!approve && userComment != null && !userComment.isBlank()) {
      return base + ": " + userComment.trim();
    }
    return base;
  }
}
