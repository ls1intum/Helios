package de.tum.cit.aet.helios.deployment.github;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.environment.Environment;
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
  }

  private void updateHeliosDeployment(Deployment deployment, Environment environment) {
    log.info(
        "Updating Helios Deployment for environment {} and branch {}",
        environment.getName(),
        deployment.getRef());
    // Linking to HeliosDeployment
    Optional<HeliosDeployment> maybeHeliosDeployment =
        heliosDeploymentRepository.findTopByEnvironmentAndBranchNameOrderByCreatedAtDesc(
            environment, deployment.getRef());
    if (maybeHeliosDeployment.isPresent()) {
      HeliosDeployment heliosDeployment = maybeHeliosDeployment.get();

      // Only update if it isn't already set
      if (heliosDeployment.getDeploymentId() == null) {
        heliosDeployment.setDeploymentId(deployment.getId());
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
      }
    }
  }
}
