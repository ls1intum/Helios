package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment.AutoApprovalDecision;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically persists the outcome of an auto-approval decision: the audit row on
 * {@link DeploymentApprovalRequest} and the decision stamp on {@link HeliosDeployment} share a
 * single transaction so a crash between the two writes cannot leave Helios in a half-state. Lives
 * in its own bean because {@code @Transactional} via self-invocation on {@link ApprovalService}
 * would be bypassed by Spring's proxy.
 */
@Service
@RequiredArgsConstructor
public class ApprovalDecisionWriter {

  private final DeploymentApprovalRequestRepository approvalRequestRepository;
  private final HeliosDeploymentRepository heliosDeploymentRepository;

  /** Records a successful auto-approval: audit row APPROVED + deployment stamped with decision. */
  @Transactional
  public void recordSuccess(
      HeliosDeployment deployment,
      DeploymentApprovalRequest auditRow,
      AutoApprovalDecision decision) {
    OffsetDateTime now = OffsetDateTime.now();
    auditRow.setState(DeploymentApprovalRequest.State.APPROVED);
    auditRow.setRespondedAt(now);
    approvalRequestRepository.save(auditRow);
    deployment.setAutoApprovalDecision(decision);
    deployment.setAutoApprovalAt(now);
    heliosDeploymentRepository.save(deployment);
  }

  /**
   * Records a failed auto-approval: audit row FAILED_AT_GITHUB with the reason, and the deployment
   * is stamped with the supplied fallback decision (DEFERRED_TO_REVIEWERS in the normal case;
   * TEAM_REVIEWER_FALLBACK if the legacy path was being attempted).
   */
  @Transactional
  public void recordFailure(
      HeliosDeployment deployment,
      DeploymentApprovalRequest auditRow,
      AutoApprovalDecision fallbackDecision,
      String reason) {
    OffsetDateTime now = OffsetDateTime.now();
    auditRow.setState(DeploymentApprovalRequest.State.FAILED_AT_GITHUB);
    auditRow.setRespondedAt(now);
    auditRow.setFailureReason(reason);
    approvalRequestRepository.save(auditRow);
    deployment.setAutoApprovalDecision(fallbackDecision);
    deployment.setAutoApprovalAt(now);
    heliosDeploymentRepository.save(deployment);
  }

  /** No-op decision stamp for the "no rule applies / nothing to do" branch. */
  @Transactional
  public void recordDecisionOnly(HeliosDeployment deployment, AutoApprovalDecision decision) {
    deployment.setAutoApprovalDecision(decision);
    deployment.setAutoApprovalAt(OffsetDateTime.now());
    heliosDeploymentRepository.save(deployment);
  }
}
