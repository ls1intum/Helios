package de.tum.cit.aet.helios.github.sync;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.branch.github.GitHubBranchSyncService;
import de.tum.cit.aet.helios.commit.github.GitHubCommitSyncService;
import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.deployment.github.DeploymentSource;
import de.tum.cit.aet.helios.deployment.github.DeploymentSourceFactory;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentDto;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentSyncService;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentDto;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentSyncService;
import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.label.github.GitHubLabelSyncService;
import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.cit.aet.helios.releaseinfo.release.github.GitHubReleaseSyncService;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import de.tum.cit.aet.helios.workflow.GitHubWorkflowContext;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowRunSyncService;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowSyncService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubDataSyncOrchestrator {
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubPullRequestSyncService pullRequestSyncService;
  private final GitHubReleaseSyncService releaseSyncService;
  private final GitHubWorkflowRunSyncService workflowRunSyncService;
  private final GitHubWorkflowSyncService workflowSyncService;
  private final GitHubBranchSyncService branchSyncService;
  private final GitHubEnvironmentSyncService environmentSyncService;
  private final GitHubDeploymentSyncService deploymentSyncService;
  private final GitHubCommitSyncService commitSyncService;
  private final GitHubLabelSyncService gitHubLabelSyncService;
  private final GitHubFacade github;
  private final GitHubService gitHubService;
  private final EnvironmentRepository environmentRepository;
  private final GitRepoRepository gitRepoRepository;
  private final DeploymentRepository deploymentRepository;
  private final DeploymentSourceFactory deploymentSourceFactory;
  private final GitHubUserSyncService gitHubUserSyncService;
  private final BranchRepository branchRepository;
  private final UserRepository userRepository;

  /**
   * Syncs a single GitHub repository by its full name (e.g., "owner/repo").
   *
   * @param nameWithOwner The full name of the repository in the format "owner/repo".
   * @return An optional containing the fetched GitHub repository, or an empty optional if the
   *     repository could not be fetched.
   */
  public Optional<GHRepository> syncRepository(String nameWithOwner) {
    try {
      var repository = github.getRepository(nameWithOwner);
      repositorySyncService.processRepository(repository);
      return Optional.of(repository);
    } catch (Exception e) {
      log.error("Failed to fetch repository {}: {}", nameWithOwner, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Synchronizes labels for a specific GitHub repository with the local repository.
   *
   * @param repository the GitHub repository whose labels are to be synchronized
   */
  public void syncLabelsOfRepository(GHRepository repository) {
    try {
      repository.listLabels().withPageSize(100).forEach(gitHubLabelSyncService::processLabel);
    } catch (IOException e) {
      log.error(
          "Failed to fetch labels for repository {}: {}", repository.getFullName(), e.getMessage());
    }
  }

  /**
   * Synchronizes all pull requests from a specific GitHub repository.
   *
   * @param repository the GitHub repository to sync pull requests from
   */
  public void syncPullRequestsOfRepository(GHRepository repository) {
    var iterator =
        repository
            .queryPullRequests()
            .state(GHIssueState.OPEN)
            .sort(GHPullRequestQueryBuilder.Sort.UPDATED)
            .direction(GHDirection.DESC)
            .list()
            .withPageSize(100)
            .iterator();

    iterator.forEachRemaining(pullRequestSyncService::processPullRequest);
  }

  /**
   * Synchronizes all environments from a specific GitHub repository.
   *
   * @param ghRepository the GitHub repository to sync environments from
   */
  public void syncEnvironmentsOfRepository(GHRepository ghRepository) {
    try {
      List<GitHubEnvironmentDto> gitHubEnvironmentDtoS =
          gitHubService.getEnvironments(ghRepository);

      for (GitHubEnvironmentDto gitHubEnvironmentDto : gitHubEnvironmentDtoS) {
        environmentSyncService.processEnvironment(gitHubEnvironmentDto, ghRepository);
      }

      // TODO: handle deletion of environments that are not present in the GitHub repository anymore
    } catch (IOException e) {
      log.error(
          "Failed to sync environments for repository {}: {}",
          ghRepository.getFullName(),
          e.getMessage());
    }
  }

  /**
   * Synchronizes deployments for a specific repository.
   *
   * @param ghRepository the GitHub repository to sync deployments from
   * @param since an optional timestamp to fetch deployments since
   */
  public void syncDeploymentsOfRepository(
      @NotNull GHRepository ghRepository, Optional<OffsetDateTime> since) {
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
   * @param environment the environment entity
   * @param since an optional timestamp to fetch deployments since
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
          user = syncUser(deploymentSource.getUserLogin());
        }

        deploymentSyncService.processDeployment(deploymentSource, gitRepository, environment, user);
      }
    } catch (Exception e) {
      log.error(
          "Failed to sync deployments for environment {}: {}",
          environment.getName(),
          e.getMessage());
    }
  }

  /**
   * Synchronizes all workflows from a specific GitHub repository.
   *
   * @param ghRepository the GitHub repository to sync workflows from
   */
  public void syncWorkflowsOfRepository(GHRepository ghRepository) {
    try {
      String fullName = ghRepository.getFullName();
      GitRepository repository = gitRepoRepository.findByNameWithOwner(fullName);
      if (repository == null) {
        log.warn("Repository {} not found in the database. Skipping workflow sync.", fullName);
        // TODO: sync repository
        return;
      }

      List<GHWorkflow> ghWorkflows = gitHubService.getWorkflows(fullName);

      for (GHWorkflow ghWorkflow : ghWorkflows) {
        workflowSyncService.processWorkflow(ghWorkflow, repository, ghRepository);
      }
    } catch (IOException e) {
      log.error(
          "Failed to sync workflows for repository {}: {}",
          ghRepository.getFullName(),
          e.getMessage());
    }
  }

  /**
   * Synchronizes all branches from a specific GitHub repository.
   *
   * @param repository the GitHub repository to sync branches from
   */
  public void syncBranchesOfRepository(GHRepository repository) {
    try {
      var branches = repository.getBranches().values().stream().toList();
      branches.forEach(branchSyncService::processBranch);
      // Get all branches for the current repository
      // var dbBranches = branchRepository.findByRepositoryRepositoryId(repository.getId());
      // TODO: We might need the old branches in some cases, so we should not delete them for now
      // Delete each branch that exists in the database and not in the fetched branches
      // dbBranches.stream()
      //   .filter(
      //      dbBranch -> branches.stream().noneMatch(b -> b.getName().equals(dbBranch.getName())))
      //     .forEach(dbBranch -> branchRepository.delete(dbBranch));
    } catch (IOException e) {
      log.error(
          "Failed to fetch branches of repository {}: {}",
          repository.getFullName(),
          e.getMessage());
    }
  }

  /**
   * Synchronizes all commits from a specific GitHub repository.
   *
   * @param repository the GitHub repository to sync commits from
   * @return a list of GitHub commits that were successfully fetched and processed
   */
  public List<GHCommit> syncCommitsOfRepository(GHRepository repository) {
    List<GHCommit> commits = new ArrayList<>();

    var dbBranches = branchRepository.findByRepositoryRepositoryId(repository.getId());
    dbBranches.forEach(
        dbBranch -> {
          try {
            var commit = repository.getCommit(dbBranch.getCommitSha());
            commitSyncService.processCommit(commit, repository);
            commits.add(commit);
          } catch (IOException e) {
            log.error(
                "Failed to fetch commit {} of repository {}: {}",
                dbBranch.getCommitSha(),
                repository.getFullName(),
                e.getMessage());
          }
        });

    var dbEnvironments =
        environmentRepository.findByRepositoryRepositoryIdOrderByCreatedAtDesc(repository.getId());
    dbEnvironments.forEach(
        dbEnvironment -> {
          try {
            if (dbEnvironment.getDeployments() == null
                || dbEnvironment.getDeployments().isEmpty()) {
              return;
            }
            var commit = repository.getCommit(dbEnvironment.getDeployments().getLast().getSha());
            log.info(
                "env: {}, sha: {}",
                dbEnvironment.getName(),
                dbEnvironment.getDeployments().getLast().getSha());
            commitSyncService.processCommit(commit, repository);
            commits.add(commit);
          } catch (IOException e) {
            log.error(
                "Failed to fetch commit {} of repository {}: {}",
                dbEnvironment.getDeployments().getLast().getSha(),
                repository.getFullName(),
                e.getMessage());
          }
        });

    // Get all commits for the current repository
    // var dbCommits = commitRepository.findByRepositoryRepositoryId(repository.getId());
    // Delete each commit that exists in the database and not in the fetched commits
    // TODO: We might need the old commits in some cases, so we should not delete them for now
    // dbCommits.stream()
    //  .filter(dbCommit -> commits.stream().noneMatch(b -> b.getSHA1().equals(dbCommit.getSha())))
    //     .forEach(dbCommit -> commitRepository.delete(dbCommit));
    return commits;
  }

  /**
   * Synchronizes all releases from a specific GitHub repository.
   *
   * @param repository the GitHub repository to sync releases from
   */
  public void syncReleasesOfRepository(GHRepository repository) {
    try {
      var iterator = repository.listReleases().withPageSize(100).iterator();

      while (iterator.hasNext()) {
        var ghReleases = iterator.nextPage();
        // Only process non-draft releases, because drafts are not unique in GitHub
        ghReleases.stream()
            .filter(Predicate.not(GHRelease::isDraft))
            .forEach((ghRelease) -> releaseSyncService.processRelease(ghRelease, repository));
      }
    } catch (IOException e) {
      log.error(
          "Failed to fetch releases for repository {}: {}",
          repository.getFullName(),
          e.getMessage());
    }
  }

  /**
   * Synchronizes all workflow runs from a specific GitHub repository.
   *
   * @param repository the GitHub repository to sync workflow runs from
   * @param since an optional date to filter workflow runs by their last update
   */
  public void syncRunsOfRepository(GHRepository repository, Optional<OffsetDateTime> since) {
    var iterator = repository.queryWorkflowRuns().list().withPageSize(100).iterator();

    var sinceDate = since.map(date -> Date.from(date.toInstant()));

    var workflowRuns = new ArrayList<GHWorkflowRun>();

    while (iterator.hasNext()) {
      var ghWorkflowRuns = iterator.nextPage();
      var keepWorkflowRuns =
          ghWorkflowRuns.stream()
              .filter(
                  ghWorkflowRun -> {
                    try {
                      return sinceDate.isEmpty()
                          || ghWorkflowRun.getUpdatedAt().after(sinceDate.get());
                    } catch (IOException e) {
                      log.error(
                          "Failed to filter workflow run {}: {}",
                          ghWorkflowRun.getId(),
                          e.getMessage());
                      return false;
                    }
                  })
              .toList();

      workflowRuns.addAll(keepWorkflowRuns);
      if (keepWorkflowRuns.size() != ghWorkflowRuns.size()) {
        break;
      }
    }

    workflowRuns.forEach(run -> {
      try {

        if (run.getEvent().equals(GHEvent.WORKFLOW_RUN)) {
          log.info("Received workflow_run event, delaying processing");
          GitHubWorkflowContext context = null;
          try {
            context =
                gitHubService.extractWorkflowContext(repository.getId(), run.getId());
          } catch (Exception e) {
            log.error("Error while extracting workflow context: {}", e.getMessage());
            return;
          }

          if (context == null) {
            log.warn("No workflow context found for workflow run: {}", run.getId());
            return;
          }

          log.info(
              "Context found with triggering workflow run id: {}, head branch: {}, head sha: {}",
              context.runId(), context.headBranch(), context.headSha());

          workflowRunSyncService.processRunWithContext(run, context);
        } else {
          workflowRunSyncService.processRun(run);
        }

      } catch (Exception e) {
        log.error(
            "Failed to process workflow run {}: {}",
            run.getId(),
            e.getMessage());
      }
    });
  }

  /**
   * Sync all existing users in the local repository with their GitHub data.
   */
  public void syncAllExistingUsers() {
    userRepository.findAll().stream().map(User::getLogin).forEach(this::syncUser);
  }

  /**
   * Sync a GitHub user's data by their login and processes it to synchronize with the local
   * repository.
   *
   * @param login The GitHub username (login) of the user to fetch.
   */
  public User syncUser(String login) {
    try {
      return gitHubUserSyncService.processUser(github.getUser(login));
    } catch (IOException e) {
      log.error("Failed to fetch user {}: {}", login, e.getMessage());
      return null;
    }
  }
}
