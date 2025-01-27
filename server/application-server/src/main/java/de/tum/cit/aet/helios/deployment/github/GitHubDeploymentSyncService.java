package de.tum.cit.aet.helios.deployment.github;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
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
  private final UserRepository userRepository;

  public GitHubDeploymentSyncService(
      DeploymentRepository deploymentRepository,
      EnvironmentRepository environmentRepository,
      GitRepoRepository gitRepoRepository,
      PullRequestRepository pullRequestRepository,
      GitHubService gitHubService,
      DeploymentConverter deploymentConverter,
      DeploymentSourceFactory deploymentSourceFactory,
      UserRepository userRepository) {
    this.deploymentRepository = deploymentRepository;
    this.environmentRepository = environmentRepository;
    this.gitRepoRepository = gitRepoRepository;
    this.pullRequestRepository = pullRequestRepository;
    this.gitHubService = gitHubService;
    this.deploymentConverter = deploymentConverter;
    this.deploymentSourceFactory = deploymentSourceFactory;
    this.userRepository = userRepository;
  }

  /**
   * Synchronizes deployments for all repositories.
   *
   * @param repositories the list of GitHub repositories to sync deployments from
   */
  public void syncDeploymentsOfAllRepositories(@NotNull List<GHRepository> repositories) {
    repositories.forEach(this::syncDeploymentsOfRepository);
  }

  /**
   * Synchronizes deployments for a specific repository.
   *
   * @param ghRepository the GitHub repository to sync deployments from
   */
  public void syncDeploymentsOfRepository(@NotNull GHRepository ghRepository) {
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
        syncDeploymentsOfEnvironment(ghRepository, environment);
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
   */
  public void syncDeploymentsOfEnvironment(
      @NotNull GHRepository ghRepository, @NotNull Environment environment) {
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
          gitHubService.getDeploymentIterator(ghRepository, environment.getName());

      while (iterator.hasNext()) {
        final GitHubDeploymentDto ghDeployment = iterator.next();
        // Set state as UNKNOWN as the state is not provided by the GitHub API
        // We can make a separate API call to fetch the deployment state
        // But that would be an overkill for now
        // Webhook handler sets the state of the deployment
        final DeploymentSource deploymentSource =
            deploymentSourceFactory.create(ghDeployment, Deployment.State.UNKNOWN);
        User user = userRepository.findById(ghDeployment.getUserId()).orElse(null);
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
   * @param environment      the associated environment entity
   */
  void processDeployment(
      @NotNull DeploymentSource deploymentSource,
      @NotNull GitRepository gitRepository,
      @NotNull Environment environment,
      @NotNull User user) {
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
    deployment.setCreator(user);

    // Save the deployment
    deploymentRepository.save(deployment);
  }
}
