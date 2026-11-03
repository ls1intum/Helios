package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.auth.github.token.GitHubReauthRequiredException;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.environment.EnvironmentService.ReviewerResolution;
import de.tum.cit.aet.helios.github.GitHubReviewException;
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

  static final String NO_LONGER_WAITING_MESSAGE =
      "This deployment is no longer waiting for approval on GitHub: it was already decided, "
          + "cancelled, or its 30-day approval window expired. It has been removed from your list.";

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
      if (isNoLongerAwaitingReview(e, repoNameWithOwner, workflowRunId)) {
        // The run was already decided, cancelled, deleted or hit GitHub's 30-day approval window.
        // Nobody can act on it any more, so archive every open row instead of leaving a dead entry
        // that fails on every click, and tell the user plainly what happened (409 = "already
        // handled"; the page refreshes its list on that).
        archiveOpenRows(existing, reviewerRow, e);
        log.info(
            "In-app {} by @{} for deployment {} skipped: run {} is no longer awaiting review.",
            approve ? "approve" : "decline",
            currentLogin,
            heliosDeploymentId,
            workflowRunId);
        throw new ResponseStatusException(HttpStatus.CONFLICT, NO_LONGER_WAITING_MESSAGE, e);
      }
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
      // Keep the client-facing status at 502: this is an upstream GitHub failure, not the Helios
      // request being unauthenticated. Returning 401 here would make the client treat the user's
      // Helios session as expired and bounce them to login.
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, gitHubFailureReason(e), e);
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

  /**
   * Whether a failed review call means the deployment can no longer be reviewed at all. GitHub
   * answers {@code 422} (and sometimes {@code 404}) in that case, but a {@code 422} also covers a
   * still-waiting deployment the user simply may not review (e.g. self-review is prevented). So the
   * status alone is not trusted: the run itself is asked, and only a missing or non-{@code waiting}
   * run counts as stale. If that check fails too, the original error is kept (never guess "stale").
   */
  private boolean isNoLongerAwaitingReview(
      IOException failure, String repoNameWithOwner, long workflowRunId) {
    if (!(failure instanceof GitHubReviewException gh)
        || (gh.getHttpStatus() != 422 && gh.getHttpStatus() != 404)) {
      return false;
    }
    try {
      return gitHubService
          .getWorkflowRunState(repoNameWithOwner, workflowRunId)
          .map(state -> !"waiting".equalsIgnoreCase(state.status()))
          .orElse(true);
    } catch (IOException checkFailure) {
      log.warn(
          "Could not verify whether run {} still awaits review: {}",
          workflowRunId,
          checkFailure.getMessage());
      return false;
    }
  }

  private void archiveOpenRows(
      List<DeploymentApprovalRequest> existingRows,
      DeploymentApprovalRequest reviewerRow,
      IOException cause) {
    OffsetDateTime now = OffsetDateTime.now();
    List<DeploymentApprovalRequest> rows = new java.util.ArrayList<>(existingRows);
    if (!rows.contains(reviewerRow)) {
      rows.add(reviewerRow);
    }
    for (DeploymentApprovalRequest row : rows) {
      if (row.getState() == DeploymentApprovalRequest.State.PENDING
          || row.getState() == DeploymentApprovalRequest.State.FAILED_AT_GITHUB
          || row == reviewerRow) {
        row.setState(DeploymentApprovalRequest.State.EXPIRED);
        row.setRespondedAt(now);
        row.setFailureReason(
            "The deployment is no longer awaiting review on GitHub (" + cause.getMessage() + ").");
        approvalRequestRepository.save(row);
      }
    }
  }

  private static boolean isTerminallyResolved(DeploymentApprovalRequest r) {
    return r.getState() == DeploymentApprovalRequest.State.APPROVED
        || r.getState() == DeploymentApprovalRequest.State.DECLINED;
  }

  /**
   * Turns a GitHub review failure into a reviewer-facing message. An {@code HTTP 401} means the
   * impersonated user's GitHub token (brokered via Keycloak) has expired — the reviewer can only
   * recover by re-authenticating — so we say so explicitly instead of leaking the raw status.
   */
  private static String gitHubFailureReason(IOException e) {
    if (e instanceof GitHubReauthRequiredException) {
      return "Your GitHub authorization can no longer be refreshed. "
          + "Please sign out of Helios and sign in again, then retry.";
    }
    if (e instanceof GitHubReviewException gh && gh.getHttpStatus() == 401) {
      return "GitHub rejected the request because your GitHub authorization has expired. "
          + "Please sign out of Helios and sign in again, then retry.";
    }
    if (e instanceof GitHubReviewException gh && gh.getHttpStatus() == 422) {
      // Only reached when the run is still waiting (otherwise it is archived as stale above).
      return "GitHub refused your decision even though the deployment is still waiting. You are "
          + "probably not allowed to review it, for example because self-review is prevented for "
          + "this environment. Check the environment's protection rules on GitHub.";
    }
    return "GitHub rejected the request: " + e.getMessage();
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
