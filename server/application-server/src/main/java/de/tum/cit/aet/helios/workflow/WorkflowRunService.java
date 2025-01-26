package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@Transactional
public class WorkflowRunService {

  private final WorkflowRunRepository workflowRunRepository;
  private final PullRequestRepository pullRequestRepository;
  private final BranchRepository branchRepository;

  public WorkflowRunService(
      WorkflowRunRepository workflowRunRepository,
      PullRequestRepository pullRequestRepository,
      BranchRepository branchRepository) {
    this.workflowRunRepository = workflowRunRepository;
    this.pullRequestRepository = pullRequestRepository;
    this.branchRepository = branchRepository;
  }

  public List<WorkflowRun> getAllWorkflowRuns() {
    return workflowRunRepository.findAll();
  }

  private Stream<WorkflowRun> getLatestWorkflowRuns(List<WorkflowRun> runs) {
    return runs.stream()
        .collect(Collectors.groupingBy(WorkflowRun::getWorkflowId))
        .values()
        .stream()
        .map(
            workflowRuns ->
                workflowRuns.stream().max(Comparator.comparing(WorkflowRun::getRunNumber)).get());
  }

  public List<WorkflowRunDto> getLatestWorkflowRunsByPullRequestIdAndHeadCommit(
      Long pullRequestId) {

    var pullRequest = pullRequestRepository.findByPullRequestId(pullRequestId).orElse(null);
    if (pullRequest == null) {
      log.error("Pull request with id {} not found!", pullRequestId);
      return List.of();
    }

    var runs =
        workflowRunRepository.findByPullRequestsIdAndHeadSha(
            pullRequestId, pullRequest.getHeadSha());
    var latestRuns = getLatestWorkflowRuns(runs).map(WorkflowRunDto::fromWorkflowRun).toList();

    // Combine pull request workflow runs with branch workflows runs if we are on the same
    // repository
    if (pullRequest
        .getHeadRefRepoNameWithOwner()
        .equals(pullRequest.getRepository().getNameWithOwner())) {
      return Stream.concat(
              latestRuns.stream(),
              getLatestWorkflowRunsByBranchAndHeadCommitSha(pullRequest.getHeadRefName()).stream())
          .collect(Collectors.toList());
    }

    return latestRuns;
  }

  public List<WorkflowRunDto> getLatestWorkflowRunsByBranchAndHeadCommitSha(String branchName) {
    var branch = branchRepository.findByName(branchName).orElseThrow();

    var runs =
        workflowRunRepository.findByHeadBranchAndHeadShaAndPullRequestsIsNull(
            branchName, branch.getCommitSha());
    var latestRuns = getLatestWorkflowRuns(runs);

    return latestRuns.map(WorkflowRunDto::fromWorkflowRun).toList();
  }
}
