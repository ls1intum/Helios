package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
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
public class DeploymentRecoveryService {

  private final DeploymentRepository deploymentRepository;
  private final HeliosDeploymentRepository heliosDeploymentRepository;

  /**
   * Scheduled task that runs every hour to find and mark stuck deployments in IN_PROGRESS
   * state as FAILURE.
   *
   * <p>The task runs at the top of every hour (cron: "0 0 * * * *").
   */
  @Scheduled(cron = "${recovery.stuck-deployments.cron:0 0 * * * *}")
  @Transactional
  public void markStuckDeploymentsAsFailure() {
    log.info("Starting stuck deployment recovery task...");
    OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);

    // Process regular deployments and Helios deployments separately
    int stuckDeploymentsCount = processStuckDeployments(oneHourAgo);
    int stuckHeliosDeploymentsCount = processStuckHeliosDeployments(oneHourAgo);

    log.info("Stuck deployment recovery completed: "
            + "Marked {} Deployment(s) as FAILURE. Marked {} HeliosDeployment(s) as FAILED.",
        stuckDeploymentsCount, stuckHeliosDeploymentsCount);
  }

  private int processStuckDeployments(OffsetDateTime timeThreshold) {
    List<Deployment> stuckDeployments =
        deploymentRepository.findStuckDeployments(timeThreshold);

    if (stuckDeployments.isEmpty()) {
      log.debug("No stuck deployments found.");
      return 0;
    }

    log.info("Found {} stuck deployment(s) in IN_PROGRESS state for more than an hour",
        stuckDeployments.size());

    int updatedCount = 0;
    for (Deployment deployment : stuckDeployments) {
      log.warn(
          "Marking deployment {} as FAILURE, stuck in IN_PROGRESS state since {}",
          deployment.getId(),
          deployment.getUpdatedAt() == null ? deployment.getCreatedAt()
              : deployment.getUpdatedAt());

      deployment.setState(Deployment.State.FAILURE);
      deploymentRepository.save(deployment);
      updatedCount++;
    }
    return updatedCount;
  }

  private int processStuckHeliosDeployments(OffsetDateTime timeThreshold) {
    List<HeliosDeployment> stuckDeployments =
        heliosDeploymentRepository.findStuckDeployments(timeThreshold);

    if (stuckDeployments.isEmpty()) {
      log.debug("No stuck Helios deployments found.");
      return 0;
    }

    log.info("Found {} stuck Helios deployment(s) in IN_PROGRESS state for more than an hour",
        stuckDeployments.size());

    int updatedCount = 0;
    for (HeliosDeployment deployment : stuckDeployments) {
      log.warn(
          "Marking Helios deployment {} as FAILED, stuck in IN_PROGRESS state since {}",
          deployment.getId(),
          deployment.getUpdatedAt());

      deployment.setStatus(HeliosDeployment.Status.FAILED);
      heliosDeploymentRepository.save(deployment);
      updatedCount++;
    }
    return updatedCount;
  }
}
