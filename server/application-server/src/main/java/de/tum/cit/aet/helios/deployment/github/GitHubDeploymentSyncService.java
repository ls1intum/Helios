package de.tum.cit.aet.helios.deployment.github;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class GitHubDeploymentSyncService {

  private final DeploymentRepository deploymentRepository;
  private final EnvironmentRepository environmentRepository;
  private final GitRepoRepository gitRepoRepository;
  private final PullRequestRepository pullRequestRepository;
  private final GitHubService gitHubService;
  private final DeploymentConverter deploymentConverter;
  private final DeploymentSourceFactory deploymentSourceFactory;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final GitHubUserSyncService gitHubUserSyncService;

  public GitHubDeploymentSyncService(
      DeploymentRepository deploymentRepository,
      EnvironmentRepository environmentRepository,
      GitRepoRepository gitRepoRepository,
      PullRequestRepository pullRequestRepository,
      GitHubService gitHubService,
      DeploymentConverter deploymentConverter,
      DeploymentSourceFactory deploymentSourceFactory,
      HeliosDeploymentRepository heliosDeploymentRepository,
      GitHubUserSyncService gitHubUserSyncService) {
    this.deploymentRepository = deploymentRepository;
    this.environmentRepository = environmentRepository;
    this.gitRepoRepository = gitRepoRepository;
    this.pullRequestRepository = pullRequestRepository;
    this.gitHubService = gitHubService;
    this.deploymentConverter = deploymentConverter;
    this.deploymentSourceFactory = deploymentSourceFactory;
    this.heliosDeploymentRepository = heliosDeploymentRepository;
    this.gitHubUserSyncService = gitHubUserSyncService;
  }

  /**
   * Synchronizes deployments for all repositories.
   *
   * @param repositories the list of GitHub repositories to sync deployments from
   * @param since        an optional timestamp to fetch deployments since
   */
  public void syncDeploymentsOfAllRepositories(
      @NotNull List<GHRepository> repositories,
      Optional<OffsetDateTime> since) {
    repositories.forEach(ghRepository -> syncDeploymentsOfRepository(ghRepository, since));
  }

  /**
   * Synchronizes deployments for a specific repository.
   *
   * @param ghRepository the GitHub repository to sync deployments from
   * @param since        an optional timestamp to fetch deployments since
   */
  public void syncDeploymentsOfRepository(
      @NotNull GHRepository ghRepository,
      Optional<OffsetDateTime> since) {
    try {
      // Fetch the GitRepository entity
      String fullName = ghRepository.getFullName();
      GitRepository repository = gitRepoRepository.findByNameWithOwner(fullName);
      if (repository == null) {
        log.warn("Repository {} not found in local database.", fullName);
        return;
      }

      // Fetch environments associated with the repository
      List<Environment> environments = environmentRepository.findByRepository(repository);

      for (Environment environment : environments) {
        syncDeploymentsOfEnvironment(ghRepository, environment, since);
      }
    } catch (Exception e) {
      log.error(
          "Failed to sync deployments for repository {}: {}",
          ghRepository.getFullName(),
          e.getMessage());
    }
  }

  /**
   * Synchronizes deployments for a specific environment.
   *
   * @param ghRepository the GitHub repository
   * @param environment  the environment entity
   * @param since        an optional timestamp to fetch deployments since
   */
  public void syncDeploymentsOfEnvironment(
      @NotNull GHRepository ghRepository,
      @NotNull Environment environment,
      Optional<OffsetDateTime> since) {
    try {
      GitRepository gitRepository =
          gitRepoRepository.findByNameWithOwner(ghRepository.getFullName());
      if (gitRepository == null) {
        // TODO: Process repository
        log.error(
            "Repository {} not found in database. Skipping deployments sync for environment {}.",
            ghRepository.getFullName(),
            environment.getName());
        return;
      }

      // Use the iterator from GitHubService to fetch deployments one by one
      Iterator<GitHubDeploymentDto> iterator =
          gitHubService.getDeploymentIterator(ghRepository, environment.getName(), since);

      while (iterator.hasNext()) {
        final GitHubDeploymentDto ghDeployment = iterator.next();

        // The data sync fetches deployments without their state,
        // as the GitHub REST API does not provide the state directly.
        // This is not ideal, but it's a limitation of the API.
        // To avoid making an additional API call to fetch the state,
        // we set it to UNKNOWN initially.
        // The state is later updated by the webhook handler during runtime.
        // However, if the data sync runs again, it could overwrite the state back to UNKNOWN.
        // To prevent this, we check if the deployment already exists in the database.
        // If it does, we skip processing to avoid overwriting the state with UNKNOWN.
        // If it doesn't, we proceed with processing the deployment.
        if (deploymentRepository.existsById(ghDeployment.getId())) {
          continue;
        }

        // Set state as UNKNOWN
        final DeploymentSource deploymentSource =
            deploymentSourceFactory.create(ghDeployment, Deployment.State.UNKNOWN);

        User user = null;
        if (deploymentSource.getUserLogin() != null) {
          // Process the creator of the deployment
          user = gitHubUserSyncService.syncUser(deploymentSource.getUserLogin());
        }

        processDeployment(deploymentSource, gitRepository, environment, user);
      }
    } catch (Exception e) {
      log.error(
          "Failed to sync deployments for environment {}: {}",
          environment.getName(),
          e.getMessage());
    }
  }

  /**
   * Processes a single DeploymentSource by updating or creating a Deployment in the local
   * repository.
   *
   * @param deploymentSource the source (GHDeployment or GitHubDeploymentDto) wrapped as a
   *                         DeploymentSource
   * @param gitRepository    the associated GitRepository entity
   * @param environment      the associated environment entity
   */
  @Transactional
  void processDeployment(
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
        pullRequestRepository.findByRepositoryRepositoryIdAndHeadRefNameOrHeadSha(
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
        heliosDeploymentRepository.save(heliosDeployment);
        log.info("Helios Deployment updated");
      }
    }
  }
}
