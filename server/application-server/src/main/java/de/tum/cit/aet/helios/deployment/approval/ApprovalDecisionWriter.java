package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment.AutoApprovalDecision;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically persists the outcome of an auto-approval decision: the audit row on
 * {@link DeploymentApprovalRequest} and the decision stamp on {@link HeliosDeployment} share a
 * single transaction so a crash between the two writes cannot leave Helios in a half-state. Lives
 * in its own bean because {@code @Transactional} via self-invocation on {@link ApprovalService}
 * would be bypassed by Spring's proxy.
 *
 * <p>Each method re-loads the deployment under a {@code SELECT ... FOR UPDATE} lock and re-checks
 * that no decision has been recorded yet. The webhook auto-approve path reads the deployment
 * <em>without</em> a lock and then calls GitHub before persisting, so a concurrent in-app
 * approve/decline (which does hold the row lock for its whole transaction) could otherwise have
 * its decision silently overwritten here, or a duplicate audit row written. Re-checking under the
 * lock makes the first recorded decision authoritative and turns a losing writer into a no-op.
 */
@Log4j2
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
    HeliosDeployment managed = lockIfStillUndecided(deployment);
    if (managed == null) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now();
    auditRow.setHeliosDeployment(managed);
    auditRow.setState(DeploymentApprovalRequest.State.APPROVED);
    auditRow.setRespondedAt(now);
    approvalRequestRepository.save(auditRow);
    managed.setAutoApprovalDecision(decision);
    managed.setAutoApprovalAt(now);
    heliosDeploymentRepository.save(managed);
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
    HeliosDeployment managed = lockIfStillUndecided(deployment);
    if (managed == null) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now();
    auditRow.setHeliosDeployment(managed);
    auditRow.setState(DeploymentApprovalRequest.State.FAILED_AT_GITHUB);
    auditRow.setRespondedAt(now);
    auditRow.setFailureReason(reason);
    approvalRequestRepository.save(auditRow);
    managed.setAutoApprovalDecision(fallbackDecision);
    managed.setAutoApprovalAt(now);
    heliosDeploymentRepository.save(managed);
  }

  /** No-op decision stamp for the "no rule applies / nothing to do" / deferral branches. */
  @Transactional
  public void recordDecisionOnly(HeliosDeployment deployment, AutoApprovalDecision decision) {
    HeliosDeployment managed = lockIfStillUndecided(deployment);
    if (managed == null) {
      return;
    }
    managed.setAutoApprovalDecision(decision);
    managed.setAutoApprovalAt(OffsetDateTime.now());
    heliosDeploymentRepository.save(managed);
  }

  /**
   * Re-loads the deployment under a pessimistic write lock and returns the managed instance only if
   * no decision has been recorded yet; returns {@code null} (caller must no-op) when another path
   * already decided. Falls back to the passed-in instance if the row can't be re-loaded.
   */
  private HeliosDeployment lockIfStillUndecided(HeliosDeployment deployment) {
    HeliosDeployment managed =
        heliosDeploymentRepository.findByIdForUpdate(deployment.getId()).orElse(deployment);
    if (managed.getAutoApprovalDecision() != null) {
      log.info(
          "Deployment {} already decided as {} by another path; skipping auto-decision write.",
          managed.getId(),
          managed.getAutoApprovalDecision());
      return null;
    }
    return managed;
  }
}
