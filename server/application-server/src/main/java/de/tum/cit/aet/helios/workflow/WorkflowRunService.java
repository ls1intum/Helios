package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
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

  public List<WorkflowRunDTO> getLatestWorkflowRunsByPullRequestIdAndHeadCommit(
      Long pullRequestId) {
    var pullRequest = pullRequestRepository.findById(pullRequestId).orElseThrow();
    var runs =
        workflowRunRepository.findByPullRequestsIdAndHeadSha(
            pullRequestId, pullRequest.getHeadSha());
    var latestRuns = getLatestWorkflowRuns(runs);

    return latestRuns.map(WorkflowRunDTO::fromWorkflowRun).toList();
  }

  public List<WorkflowRunDTO> getLatestWorkflowRunsByBranchAndHeadCommitSha(String branchName) {
    var branch = branchRepository.findByName(branchName).orElseThrow();

    var runs =
        workflowRunRepository.findByHeadBranchAndHeadShaAndPullRequestsIsNull(
            branchName, branch.getCommit_sha());
    var latestRuns = getLatestWorkflowRuns(runs);

    return latestRuns.map(WorkflowRunDTO::fromWorkflowRun).toList();
  }
}
