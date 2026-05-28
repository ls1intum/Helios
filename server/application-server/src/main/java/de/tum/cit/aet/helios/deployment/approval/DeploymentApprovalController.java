package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.user.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints for a logged-in Helios user to act on deployments that are waiting on a required
 * reviewer — without leaving Helios for the GitHub UI. See {@link DeploymentReviewActionService}
 * for the business logic.
 *
 * <p>Authorization: authentication is required (enforced globally by Spring Security); the more
 * specific "is the current user a required reviewer for this env?" check happens inside the
 * action service, so the same endpoint can serve a maintainer who deployed and is also a reviewer
 * as well as a non-deploying reviewer.
 */
@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
public class DeploymentApprovalController {

  private final DeploymentReviewActionService reviewActionService;
  private final DeploymentApprovalRequestRepository approvalRequestRepository;
  private final AuthService authService;

  @GetMapping("/pending-approvals")
  public ResponseEntity<List<PendingApprovalDto>> myPendingApprovals() {
    User currentUser = authService.getUserFromGithubId();
    if (currentUser == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated user.");
    }
    List<PendingApprovalDto> result =
        approvalRequestRepository.findPendingForReviewer(currentUser.getId()).stream()
            .map(PendingApprovalDto::from)
            .toList();
    return ResponseEntity.ok(result);
  }

  @PostMapping("/{deploymentId}/approve")
  public ResponseEntity<ApprovalDecisionDto> approve(@PathVariable Long deploymentId) {
    User currentUser = authService.getUserFromGithubId();
    DeploymentApprovalRequest row =
        reviewActionService.approveAsCurrentUser(deploymentId, currentUser);
    return ResponseEntity.ok(ApprovalDecisionDto.from(row));
  }

  @PostMapping("/{deploymentId}/decline")
  public ResponseEntity<ApprovalDecisionDto> decline(
      @PathVariable Long deploymentId,
      @Valid @RequestBody(required = false) ReviewDeploymentRequest body) {
    User currentUser = authService.getUserFromGithubId();
    String comment = body == null ? null : body.comment();
    DeploymentApprovalRequest row =
        reviewActionService.declineAsCurrentUser(deploymentId, currentUser, comment);
    return ResponseEntity.ok(ApprovalDecisionDto.from(row));
  }

  /** Outcome echo so the UI can advance state without re-fetching the list. */
  public record ApprovalDecisionDto(
      Long approvalRequestId,
      Long deploymentId,
      DeploymentApprovalRequest.State state,
      DeploymentApprovalRequest.Via via) {
    public static ApprovalDecisionDto from(DeploymentApprovalRequest row) {
      return new ApprovalDecisionDto(
          row.getId(), row.getHeliosDeployment().getId(), row.getState(), row.getVia());
    }
  }
}
