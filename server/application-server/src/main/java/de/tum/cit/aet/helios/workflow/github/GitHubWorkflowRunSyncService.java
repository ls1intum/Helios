package de.tum.cit.aet.helios.workflow.github;

import static de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment.mapWorkflowRunStatus;

import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.util.DateUtil;
import de.tum.cit.aet.helios.workflow.GitHubWorkflowContext;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import de.tum.cit.aet.helios.workflow.WorkflowService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHWorkflowRun;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class GitHubWorkflowRunSyncService {
  private final WorkflowRunRepository workflowRunRepository;
  private final GitHubWorkflowRunConverter workflowRunConverter;
  private final GitRepoRepository gitRepoRepository;
  private final PullRequestRepository pullRequestRepository;
  private final WorkflowRepository workflowRepository;
  private final WorkflowService workflowService;
  private final HeliosDeploymentRepository heliosDeploymentRepository;

  @Transactional
  public WorkflowRun processRun(GHWorkflowRun ghWorkflowRun) {
    return process(ghWorkflowRun, null);
  }

  @Transactional
  public WorkflowRun processRunWithContext(
      GHWorkflowRun ghWorkflowRun, GitHubWorkflowContext context) {
    return process(ghWorkflowRun, context);
  }

  private WorkflowRun process(GHWorkflowRun ghWorkflowRun,
                              GitHubWorkflowContext context) {
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

    if (result.getWorkflow() == null) {
      var workflow = workflowRepository.findById(ghWorkflowRun.getWorkflowId());

      // We don't want to create runs for workflows that are not in our database
      if (workflow.isEmpty()) {
        log.warn(
            "Workflow {} not found in database, skipping workflow run {}",
            ghWorkflowRun.getWorkflowId(),
            ghWorkflowRun.getId());
        return null;
      }

      result.setWorkflow(workflow.get());
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

    // If we have a context, set triggeredWorkflowRunId, headBranch, headSha
    // And find matching open PR if available.
    if (context != null) {
      log.info("Applying GitHubWorkflowContext: runId={}, branch={}, sha={}",
          context.runId(), context.headBranch(), context.headSha());

      result.setTriggeredWorkflowRunId(context.runId());
      result.setHeadBranch(context.headBranch());
      result.setHeadSha(context.headSha());

      // Find a matching open PR
      if (result.getRepository() != null) {
        var repositoryId = result.getRepository().getRepositoryId();
        pullRequestRepository
            .findOpenPrByBranchNameOrSha(repositoryId, context.headBranch(), context.headSha())
            .ifPresent(pr -> result.setPullRequests(new HashSet<>(Collections.singletonList(pr))

            ));
      }
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

    workflowRunRepository.save(result);

    return result;
  }

  private void processRunForHeliosDeployment(GHWorkflowRun workflowRun) throws IOException {
    // Get the deployment workflow set by the managers
    List<Workflow> deploymentWorkflows =
        workflowService.getDeploymentWorkflowsForAllEnv(workflowRun.getRepository().getId());
    if (deploymentWorkflows.isEmpty()) {
      log.debug(
          "No deployment workflow found while processing workflow run {}", workflowRun.getId());
      return;
    }

    boolean isDeploymentWorkflowRun =
        deploymentWorkflows.stream()
            .anyMatch(workflow -> workflow.getId().equals(workflowRun.getWorkflowId()));

    if (!isDeploymentWorkflowRun) {
      log.debug("Workflow run {} is not a deployment workflow run", workflowRun.getId());

      return;
    }

    // TODO: We need to check whether workflow run is triggered via Helios-App or via the Github UI
    // Library that we are using didin't implement this feature. We need to get the user who
    // triggered the workflow run
    // Then we can check whether it's triggered via Helios-App or a Github User via Github UI.
    // We only need to update heliosDeployment if it's triggered via Helios-App
    heliosDeploymentRepository
        .findTopByBranchNameAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            workflowRun.getHeadBranch(),
            DateUtil.convertToOffsetDateTime(workflowRun.getRunStartedAt()))
        .ifPresent(
            heliosDeployment -> {
              try {
                if (workflowRun
                    .getUpdatedAt()
                    .toInstant()
                    .isAfter(heliosDeployment.getUpdatedAt().toInstant())
                    || workflowRun
                    .getUpdatedAt()
                    .toInstant()
                    .equals(heliosDeployment.getUpdatedAt().toInstant())) {
                  heliosDeployment.setUpdatedAt(
                      DateUtil.convertToOffsetDateTime(workflowRun.getUpdatedAt()));
                  HeliosDeployment.Status mappedStatus =
                      mapWorkflowRunStatus(workflowRun.getStatus(), workflowRun.getConclusion());
                  log.debug("Mapped status {} to {}", workflowRun.getStatus(), mappedStatus);

                  // Update the deployment status
                  heliosDeployment.setStatus(mappedStatus);

                  // Update the workflow run html url, so we can show the approval url
                  // to the user before the Github deployment is created
                  heliosDeployment.setWorkflowRunId(workflowRun.getId());
                  heliosDeployment.setWorkflowRunHtmlUrl(workflowRun.getHtmlUrl().toString());

                  log.info(
                      "Updated HeliosDeployment {} to status {}",
                      heliosDeployment.getId(),
                      mappedStatus);
                  heliosDeploymentRepository.save(heliosDeployment);
                }
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
  }
}
