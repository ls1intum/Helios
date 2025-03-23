package de.tum.cit.aet.helios.deployment.github;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.approval.ApprovalService;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistory;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistoryRepository;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.sync.GitHubDataSyncOrchestrator;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHDeployment;
import org.kohsuke.github.GHDeploymentStatus;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class GitHubDeploymentStatusMessageHandler
    extends GitHubMessageHandler<GHEventPayload.DeploymentStatus> {

  private final GitHubDeploymentSyncService deploymentSyncService;
  private final ApprovalService approvalService;
  private final GitRepoRepository gitRepoRepository;
  private final EnvironmentRepository environmentRepository;
  private final DeploymentSourceFactory deploymentSourceFactory;
  private final GitHubUserSyncService userSyncService;
  private final EnvironmentLockHistoryRepository environmentLockHistoryRepository;
  private final GitHubFacade github;
  private final GitHubDataSyncOrchestrator gitHubDataSyncOrchestrator;

  private final Instant applicationStartTime = Instant.now();

  @Override
  protected Class<GHEventPayload.DeploymentStatus> getPayloadClass() {
    return GHEventPayload.DeploymentStatus.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.DEPLOYMENT_STATUS;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.DeploymentStatus eventPayload) {
    log.info(
        "Received deployment status event for repository: {}, deployment: {}, action: {}",
        eventPayload.getRepository().getFullName(),
        eventPayload.getDeployment().getId(),
        eventPayload.getAction());

    GHDeployment ghDeployment = eventPayload.getDeployment();

    User convertedUser = null;
    try {
      GHUser user = ghDeployment.getCreator();
      if (user != null) {
        convertedUser = userSyncService.processUser(user);
      }
    } catch (IOException e) {
      log.error("Error while getting creator of deployment {}", ghDeployment.getId());
      return;
    }

    // Extract environment name
    String environmentName = ghDeployment.getEnvironment();
    if (environmentName == null || environmentName.isEmpty()) {
      log.error("Deployment {} has no environment name", ghDeployment.getId());
      return;
    }

    // Get the repository entity
    GHRepository ghRepository = eventPayload.getRepository();
    final GitRepository repository =
        gitRepoRepository.findByRepositoryId(ghRepository.getId()).orElse(null);
    if (repository == null) {
      log.warn(
          "Repository {} not found in the database. Skipping deployment event.",
          ghRepository.getFullName());
      return;
    }

    // Find the corresponding Environment entity
    Environment environment =
        environmentRepository.findByNameAndRepository(environmentName, repository);
    if (environment == null) {
      log.warn(
          "Environment {} not found for repository {}. Syncing environments for the repository"
              + " started.",
          environmentName,
          repository.getNameWithOwner());
      // Sync environments of the repository
      gitHubDataSyncOrchestrator.syncEnvironmentsOfRepository(eventPayload.getRepository());

      // Re-check for the environment after syncing
      environment = environmentRepository.findByNameAndRepository(environmentName, repository);
      if (environment == null) {
        log.error(
            "Environment {} not found for repository {}. Deployment event is ignored.",
            environmentName,
            repository.getNameWithOwner());
        return;
      }
    }

    // If we have user name coming from the deployment event, and it is the helios app name, then
    // change it to locking user
    if (convertedUser != null && (convertedUser.getLogin().equals(github.getGithubAppName()))) {
      Optional<EnvironmentLockHistory> lockHistory =
          environmentLockHistoryRepository.findCurrentLockForEnabledEnvironment(
              environment.getId());
      if (lockHistory.isPresent() && lockHistory.get().getLockedBy() != null) {
        convertedUser = lockHistory.get().getLockedBy();
      }
    }

    // Get the deployment status.
    // Has fields "description", "state", "createdAt", "id", "nodeId", "updatedAt", "url"
    // State can be "success", "error", "pending", "waiting", etc.
    // org.kohsuke.github.GHDeploymentStatus didn't implement the state WAITING
    // So calling eventPayload.getDeploymentStatus().getState() will throw an exception
    // No enum constant org.kohsuke.github.GHDeploymentState.WAITING
    // Before calling getState() method, check if the state is WAITING and handle it gracefully
    // Deployment.mapToState handles mapping gracefully
    GHDeploymentStatus deploymentStatus = eventPayload.getDeploymentStatus();
    // Convert GHDeployment to DeploymentSource
    DeploymentSource deploymentSource =
        deploymentSourceFactory.create(ghDeployment, Deployment.mapToState(deploymentStatus));

    // Process this single deployment
    deploymentSyncService.processDeployment(
        deploymentSource, repository, environment, convertedUser);

    // Auto-approve deployments in WAITING state
    if (deploymentSource.getState() == Deployment.State.WAITING) {
      // Check if this is a recent event (after app startup) or an old event being replayed
      Instant eventTime;
      try {
        eventTime = eventPayload.getDeploymentStatus().getCreatedAt().toInstant();
      } catch (IOException e) {
        eventTime = Instant.now();
      }

      // Only automatically approve if the event is recent (after application startup)
      if (eventTime.isAfter(applicationStartTime)) {
        log.info("Recent deployment in WAITING state. Reviewing the deployment.");
        approvalService.reviewDeployment(deploymentSource, repository, environment, convertedUser);
      }
    }
  }
}
