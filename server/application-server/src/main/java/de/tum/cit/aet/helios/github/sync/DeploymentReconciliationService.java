package de.tum.cit.aet.helios.github.sync;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class DeploymentReconciliationService {

  private static final long STALE_AFTER_MINUTES = 60L;
  private static final int BATCH_SIZE = 100;

  private static final List<Deployment.State> INCOMPLETE_DEPLOYMENT_STATES =
      List.of(
          Deployment.State.PENDING,
          Deployment.State.WAITING,
          Deployment.State.QUEUED,
          Deployment.State.IN_PROGRESS);

  @Value("${reconciliation.enabled:true}")
  private boolean enabled;

  private final DeploymentRepository deploymentRepository;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final GitHubService gitHubService;

  @Scheduled(cron = "${reconciliation.deployments.cron:0 */15 * * * *}")
  public void reconcileStaleDeployments() {
    if (!enabled) {
      log.debug("Deployment reconciliation is disabled.");
      return;
    }

    OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(STALE_AFTER_MINUTES);
    int processed = 0;
    int updated = 0;
    int pages = 0;
    OffsetDateTime cursorTime = null;
    long cursorId = 0L;

    while (true) {
      List<Deployment> staleDeployments = deploymentRepository.findStaleIncompleteDeployments(
          threshold,
          INCOMPLETE_DEPLOYMENT_STATES,
          cursorTime,
          cursorId,
          PageRequest.of(0, BATCH_SIZE));

      if (staleDeployments.isEmpty()) {
        break;
      }

      pages++;
      processed += staleDeployments.size();
      Deployment lastInBatch = staleDeployments.getLast();
      OffsetDateTime nextCursorTime = getSortKey(lastInBatch);
      long nextCursorId = lastInBatch.getId();

      for (Deployment deployment : staleDeployments) {
        String repositoryNameWithOwner = resolveRepositoryNameWithOwner(deployment.getRepository());
        if (repositoryNameWithOwner == null) {
          log.warn("Skipping stale deployment {} because repository is missing.",
              deployment.getId());
          continue;
        }

        try {
          Optional<GitHubService.DeploymentState> remoteState =
              gitHubService.getLatestDeploymentState(repositoryNameWithOwner, deployment.getId());
          if (remoteState.isEmpty()) {
            continue;
          }

          if (applyDeploymentState(deployment, remoteState.get())) {
            updated++;
          }
        } catch (IOException ex) {
          log.warn(
              "Failed to reconcile deployment {} in repository {}: {}",
              deployment.getId(),
              repositoryNameWithOwner,
              ex.getMessage());
        }
      }

      cursorTime = nextCursorTime;
      cursorId = nextCursorId;
    }

    log.info(
        "Deployment reconciliation finished. Processed {} stale deployment(s) across {} page(s);"
            + " updated {} deployment(s).",
        processed,
        pages,
        updated);
  }

  private boolean applyDeploymentState(
      Deployment deployment, GitHubService.DeploymentState remoteState) {
    Deployment.State mappedState = mapDeploymentState(remoteState.state());
    if (mappedState == null) {
      return false;
    }

    OffsetDateTime remoteUpdatedAt = remoteState.updatedAt();
    boolean stateChanged = deployment.getState() != mappedState;
    boolean timestampAdvanced = isRemoteTimestampNewer(remoteUpdatedAt, deployment.getUpdatedAt());

    if (!stateChanged && !timestampAdvanced) {
      return false;
    }

    if (stateChanged) {
      deployment.setState(mappedState);
    }
    if (timestampAdvanced) {
      deployment.setUpdatedAt(remoteUpdatedAt);
    }
    deploymentRepository.save(deployment);

    synchronizeExistingHeliosDeployment(deployment);
    return true;
  }

  private void synchronizeExistingHeliosDeployment(Deployment deployment) {
    heliosDeploymentRepository
        .findByDeploymentId(deployment.getId())
        .ifPresent(heliosDeployment -> updateHeliosDeployment(heliosDeployment, deployment));
  }

  private void updateHeliosDeployment(HeliosDeployment heliosDeployment, Deployment deployment) {
    HeliosDeployment.Status mappedStatus =
        HeliosDeployment.mapDeploymentStateToHeliosStatus(deployment.getState());
    boolean statusChanged = heliosDeployment.getStatus() != mappedStatus;
    boolean timestampAdvanced =
        isRemoteTimestampNewer(deployment.getUpdatedAt(), heliosDeployment.getUpdatedAt());

    if (!statusChanged && !timestampAdvanced) {
      return;
    }

    if (statusChanged) {
      heliosDeployment.setStatus(mappedStatus);
    }

    if (timestampAdvanced) {
      heliosDeployment.setUpdatedAt(deployment.getUpdatedAt());
    }

    heliosDeploymentRepository.save(heliosDeployment);
  }

  private Deployment.State mapDeploymentState(String rawState) {
    if (rawState == null || rawState.isBlank()) {
      return null;
    }
    return switch (rawState.trim().toUpperCase(Locale.ROOT)) {
      case "PENDING" -> Deployment.State.PENDING;
      case "WAITING" -> Deployment.State.WAITING;
      case "SUCCESS" -> Deployment.State.SUCCESS;
      case "ERROR" -> Deployment.State.ERROR;
      case "FAILURE", "FAILED" -> Deployment.State.FAILURE;
      case "IN_PROGRESS" -> Deployment.State.IN_PROGRESS;
      case "QUEUED" -> Deployment.State.QUEUED;
      case "INACTIVE" -> Deployment.State.INACTIVE;
      case "CANCELLED" -> Deployment.State.CANCELLED;
      default -> Deployment.State.UNKNOWN;
    };
  }

  private boolean isRemoteTimestampNewer(OffsetDateTime remote, OffsetDateTime local) {
    return remote != null && (local == null || remote.isAfter(local));
  }

  private OffsetDateTime getSortKey(Deployment deployment) {
    return deployment.getUpdatedAt() != null
        ? deployment.getUpdatedAt() : deployment.getCreatedAt();
  }

  private String resolveRepositoryNameWithOwner(GitRepository repository) {
    return repository == null ? null : repository.getNameWithOwner();
  }
}
