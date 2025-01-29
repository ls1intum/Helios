package de.tum.cit.aet.helios.workflow.github;

import static de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment.mapWorkflowRunStatus;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.util.DateUtil;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import de.tum.cit.aet.helios.workflow.WorkflowService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class GitHubWorkflowRunSyncService {
  private final WorkflowRunRepository workflowRunRepository;
  private final GitHubWorkflowRunConverter workflowRunConverter;
  private final GitRepoRepository gitRepoRepository;
  private final PullRequestRepository pullRequestRepository;
  private final WorkflowService workflowService;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final BranchRepository branchRepository;

  public GitHubWorkflowRunSyncService(
      WorkflowRunRepository workflowRunRepository,
      GitHubWorkflowRunConverter workflowRunConverter,
      GitRepoRepository gitRepoRepository,
      PullRequestRepository pullRequestRepository, WorkflowService workflowService,
      HeliosDeploymentRepository heliosDeploymentRepository, BranchRepository branchRepository) {
    this.workflowRunRepository = workflowRunRepository;
    this.workflowRunConverter = workflowRunConverter;
    this.gitRepoRepository = gitRepoRepository;
    this.pullRequestRepository = pullRequestRepository;
    this.workflowService = workflowService;
    this.heliosDeploymentRepository = heliosDeploymentRepository;
    this.branchRepository = branchRepository;
  }

  /**
   * Synchronizes all workflow runs from the specified GitHub repositories.
   *
   * @param repositories the list of GitHub repositories to sync workflow runs from
   * @return a list of GitHub workflow runs that were successfully fetched and processed
   */
  public List<GHWorkflowRun> syncLatestRunsOfWorkflowsOfAllRepositories(
      List<GHRepository> repositories) {
    return repositories.stream()
        .map(repository -> {
          try {
            return syncRunsOfRepository(repository);
          } catch (IOException e) {
            log.error("Failed to sync runs of repository {}: {}", repository.getName(), e.getMessage());
            return new ArrayList<GHWorkflowRun>();
          }
        })
        .flatMap(List::stream)
        .toList();
  }

  /**
   * Synchronizes all workflow runs from a specific GitHub repository.
   *
   * @param repository the GitHub repository to sync workflow runs from
   * @return a list of GitHub workflow runs requests that were successfully fetched and processed
   */
  public List<GHWorkflowRun> syncRunsOfRepository(
      GHRepository repository) throws IOException {

    // Get all workflows, such as deploy.yml, test.yml, etc.
    List<GHWorkflow> workflows = repository.listWorkflows().toList();

    // Get all branches of the repository
    // Branches should be synced before running WorkflowRun sync
    List<Branch> branchList = branchRepository.findByRepositoryRepositoryId(repository.getId());


    // For each workflow, get the latest workflow runs for each branch
    for (GHWorkflow workflow : workflows) {
      for (Branch branch : branchList) {
        // TODO: implement here

      }
    }


    var iterator = repository.queryWorkflowRuns().list().withPageSize(100).iterator();

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

    workflowRuns.forEach(this::processRun);

    return workflowRuns;
  }

  @Transactional
  public WorkflowRun processRun(GHWorkflowRun ghWorkflowRun) {
    var result =
        workflowRunRepository
            .findById(ghWorkflowRun.getId())
            .map(
                workflowRun -> {
                  try {
                    if (workflowRun.getUpdatedAt() == null
                        || workflowRun
                        .getUpdatedAt()
                        .isBefore(
                            DateUtil.convertToOffsetDateTime(ghWorkflowRun.getUpdatedAt()))) {
                      return workflowRunConverter.update(ghWorkflowRun, workflowRun);
                    }
                    return workflowRun;
                  } catch (IOException e) {
                    log.error(
                        "Failed to update worfklow run {}: {}",
                        ghWorkflowRun.getId(),
                        e.getMessage());
                    return null;
                  }
                })
            .orElseGet(() -> workflowRunConverter.convert(ghWorkflowRun));

    if (result == null) {
      return null;
    }

    // Link with existing repository if not already linked
    if (result.getRepository() == null) {
      var nameWithOwner = ghWorkflowRun.getRepository().getFullName();
      var repository = gitRepoRepository.findByNameWithOwner(nameWithOwner);

      if (repository != null) {
        result.setRepository(repository);
      }
    }

    try {
      Set<PullRequest> pullRequests = new HashSet<>();

      ghWorkflowRun
          .getPullRequests()
          .forEach(
              pullRequest -> {
                var pr = pullRequestRepository.findById(pullRequest.getId());

                if (!pr.isEmpty()) {
                  pullRequests.add(pr.get());
                }
              });

      result.setPullRequests(pullRequests);
    } catch (IOException e) {
      log.error(
          "Failed to process pull requests for workflow run {}: {}",
          ghWorkflowRun.getId(),
          e.getMessage());
    }

    // Process the workflow run for HeliosDeployment
    try {
      processRunForHeliosDeployment(ghWorkflowRun);
    } catch (IOException e) {
      log.error(
          "Failed to process workflow run {} for HeliosDeployment: {}",
          ghWorkflowRun.getId(),
          e.getMessage());
    }

    return workflowRunRepository.save(result);
  }

  private void processRunForHeliosDeployment(GHWorkflowRun workflowRun) throws IOException {
    // Get the deployment workflow set by the managers
    Workflow deploymentWorkflow = workflowService.getDeploymentWorkflow();
    if (deploymentWorkflow == null) {
      return;
    }

    if (workflowRun.getWorkflowId() != deploymentWorkflow.getId()) {
      return;
    }

    heliosDeploymentRepository
        .findTopByBranchNameAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            workflowRun.getHeadBranch(),
            DateUtil.convertToOffsetDateTime(workflowRun.getRunStartedAt()))
        .ifPresent(heliosDeployment -> {

          HeliosDeployment.Status mappedStatus = mapWorkflowRunStatus(
              workflowRun.getStatus(),
              workflowRun.getConclusion()
          );
          log.debug("Mapped status {} to {}", workflowRun.getStatus(), mappedStatus);

          // Update the deployment status
          heliosDeployment.setStatus(mappedStatus);
          log.info("Updated HeliosDeployment {} to status {}", heliosDeployment.getId(),
              mappedStatus);
          heliosDeploymentRepository.save(heliosDeployment);
        });

  }
}
