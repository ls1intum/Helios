package de.tum.cit.aet.helios.deployment.github;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.ws.EnvironmentDeploymentWebSocketPublisher;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.user.User;
import jakarta.transaction.Transactional;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubDeploymentSyncService {

  private final DeploymentRepository deploymentRepository;
  private final PullRequestRepository pullRequestRepository;
  private final DeploymentConverter deploymentConverter;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final EnvironmentDeploymentWebSocketPublisher environmentDeploymentWebSocketPublisher;

  /**
   * Processes a single DeploymentSource by updating or creating a Deployment in the local
   * repository.
   *
   * @param deploymentSource the source (GHDeployment or GitHubDeploymentDto) wrapped as a
   *     DeploymentSource
   * @param gitRepository the associated GitRepository entity
   * @param environment the associated environment entity
   */
  @Transactional
  public void processDeployment(
      @NotNull DeploymentSource deploymentSource,
      @NotNull GitRepository gitRepository,
      @NotNull Environment environment,
      User user) {
    Deployment deployment =
        deploymentRepository.findById(deploymentSource.getId()).orElseGet(Deployment::new);

    deploymentConverter.update(deploymentSource, deployment);

    // Set the associated environment
    deployment.setEnvironment(environment);
    // Set the repository
    deployment.setRepository(gitRepository);

    // Set the PR associated with the deployment
    Optional<PullRequest> optionalPullRequest =
        pullRequestRepository.findOpenPrByBranchNameOrSha(
            gitRepository.getRepositoryId(), deployment.getRef(), deployment.getSha());

    optionalPullRequest.ifPresent(deployment::setPullRequest);

    // Set the creator of the deployment
    if (user != null && deployment.getCreator() == null) {
      deployment.setCreator(user);
    }

    // Save the deployment
    deploymentRepository.save(deployment);

    // Update Helios Deployment
    updateHeliosDeployment(deployment, environment);
    environmentDeploymentWebSocketPublisher.publishAfterCommit(environment);
  }

  private void updateHeliosDeployment(Deployment deployment, Environment environment) {
    log.info(
        "Updating Helios Deployment for environment {} and branch {}",
        environment.getName(),
        deployment.getRef());

    // Try exact match by workflowRunId first (available from webhook payloads).
    // The REST deployments API does not expose workflow_run_id, so REST-synced deployments
    // always fall through to the env+branch heuristic.
    // Skip any HeliosDeployment that already has a different workflowRunId — that belongs to
    // a different run.
    Optional<HeliosDeployment> maybeHeliosDeployment = Optional.empty();

    if (deployment.getWorkflowRunId() != null) {
      maybeHeliosDeployment =
          heliosDeploymentRepository.findByWorkflowRunId(deployment.getWorkflowRunId());
    }

    if (maybeHeliosDeployment.isEmpty()) {
      maybeHeliosDeployment =
          heliosDeploymentRepository
              .findTopByEnvironmentAndBranchNameOrderByCreatedAtDesc(
                  environment, deployment.getRef())
              .filter(
                  hd ->
                      hd.getWorkflowRunId() == null
                          || (deployment.getWorkflowRunId() != null
                              && hd.getWorkflowRunId().equals(deployment.getWorkflowRunId())));
    }

    maybeHeliosDeployment.ifPresent(heliosDeployment -> {
      if (heliosDeployment.getDeploymentId() == null) {
        heliosDeployment.setDeploymentId(deployment.getId());
      }
      heliosDeployment.setStatus(
          HeliosDeployment.mapDeploymentStateToHeliosStatus(deployment.getState()));
      if (deployment
          .getUpdatedAt()
          .toInstant()
          .isAfter(heliosDeployment.getUpdatedAt().toInstant())) {
        heliosDeployment.setUpdatedAt(deployment.getUpdatedAt());
      }
      heliosDeploymentRepository.save(heliosDeployment);
      log.info("Helios Deployment updated");
    });
  }
}
