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

  @Transactional
  public DeploymentApprovalRequest approveAsCurrentUser(Long heliosDeploymentId, User currentUser) {
    return review(heliosDeploymentId, currentUser, true, null);
  }

  @Transactional
  public DeploymentApprovalRequest declineAsCurrentUser(
      Long heliosDeploymentId, User currentUser, String comment) {
    return review(heliosDeploymentId, currentUser, false, comment);
  }

  /**
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

    if (!reviewers.userLogins().contains(currentLogin)) {
      // Team-type reviewers (which Helios doesn't yet expand) deliberately don't satisfy this
      // check from in-app; those approvals still need to happen on GitHub for now.
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
              // No token issued for an in-app click; use now() to satisfy NOT NULL.
              row.setExpiresAt(OffsetDateTime.now());
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
