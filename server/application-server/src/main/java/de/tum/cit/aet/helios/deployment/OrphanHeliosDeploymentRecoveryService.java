package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class OrphanHeliosDeploymentRecoveryService {

  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final HeliosDeploymentWorkflowRunSyncService heliosDeploymentWorkflowRunSyncService;

  /**
   * Scheduled task that runs every hour and force-finalizes very old orphan Helios deployments.
   *
   * <p>This is a last-resort fallback and should only apply after the regular reconciliation has
   * had enough time to sync true state from GitHub.
   *
   * <p>The task runs at the top of every hour (cron: "0 0 * * * *").
   */
  @Scheduled(cron = "${recovery.orphan-helios-deployments.cron:0 0 * * * *}")
  @Transactional
  public void markStuckOrphanHeliosDeploymentsAsFailure() {
    log.info("Starting orphan Helios deployment recovery task...");
    OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);

    int synchronizedCount = synchronizeStuckWorkflowRunBackedDeployments(oneHourAgo);
    int recoveredCount = processStuckHeliosDeployments(oneHourAgo);

    log.info(
        "Orphan Helios deployment recovery completed: synchronized {} workflow-backed"
            + " deployment(s), marked {} deployment(s) without workflowRunId as FAILED.",
        synchronizedCount,
        recoveredCount);
  }

  private int synchronizeStuckWorkflowRunBackedDeployments(OffsetDateTime timeThreshold) {
    List<HeliosDeployment> stuckDeployments =
        heliosDeploymentRepository.findStuckDeploymentsWithWorkflowRunId(timeThreshold);

    int synchronizedCount = 0;
    for (HeliosDeployment deployment : stuckDeployments) {
      String repositoryNameWithOwner =
          deployment.getEnvironment().getRepository().getNameWithOwner();
      boolean wasSynchronized = false;
      try {
        wasSynchronized =
            heliosDeploymentWorkflowRunSyncService.synchronizeTerminalStateFromWorkflowRun(
                repositoryNameWithOwner, deployment.getWorkflowRunId());
      } catch (IOException e) {
        log.warn(
            "Failed to synchronize stuck Helios deployment {} from workflow run {}: {}",
            deployment.getId(),
            deployment.getWorkflowRunId(),
            e.getMessage());
      }

      if (wasSynchronized) {
        synchronizedCount++;
      } else if (deployment.getDeploymentId() == null) {
        // Preserve the original safety net: orphan rows (no linked Deployment) must be
        // force-finalized after the threshold even when GitHub did not yield a terminal state.
        markStuckHeliosDeploymentAsFailed(deployment);
      }
    }

    return synchronizedCount;
  }

  private int processStuckHeliosDeployments(OffsetDateTime timeThreshold) {
    List<HeliosDeployment> stuckDeployments =
        heliosDeploymentRepository
            .findStuckDeploymentsWithoutDeploymentIdAndWorkflowRunIdIsNull(timeThreshold);

    if (stuckDeployments.isEmpty()) {
      log.debug("No stuck Helios deployments found.");
      return 0;
    }

    log.info(
        "Found {} stuck Helios deployment(s) in incomplete state for more than an hour",
        stuckDeployments.size());

    int updatedCount = 0;
    for (HeliosDeployment deployment : stuckDeployments) {
      markStuckHeliosDeploymentAsFailed(deployment);
      updatedCount++;
    }
    return updatedCount;
  }

  private void markStuckHeliosDeploymentAsFailed(HeliosDeployment deployment) {
    log.warn(
        "Marking Helios deployment {} as FAILED, stuck in {} state since {}",
        deployment.getId(),
        deployment.getStatus(),
        deployment.getStatusUpdatedAt());

    deployment.setStatus(HeliosDeployment.Status.FAILED);
    heliosDeploymentRepository.save(deployment);
  }
}
