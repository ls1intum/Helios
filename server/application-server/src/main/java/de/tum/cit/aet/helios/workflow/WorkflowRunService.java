package de.tum.cit.aet.helios.workflow;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class WorkflowRunService {

    private final WorkflowRunRepository workflowRunRepository;

    public WorkflowRunService(WorkflowRunRepository workflowRunRepository) {
        this.workflowRunRepository = workflowRunRepository;
    }

    public List<WorkflowRun> getAllWorkflowRuns() {
        return workflowRunRepository.findAll();
    }
    
    private Stream<WorkflowRun> getLatestWorkflowRuns(List<WorkflowRun> runs) {
        return runs.stream()
            .collect(Collectors.groupingBy(WorkflowRun::getWorkflowId))
            .values()
            .stream()
            .map(workflowRuns -> workflowRuns.stream().max(Comparator.comparing(WorkflowRun::getRunNumber)).get());
    }

    public List<WorkflowRunDTO> getLatestWorkflowRunsByPullRequestIdAndHeadCommitSha(
        Long pullRequestId,
        String headCommitSha
    ) {
        var runs = workflowRunRepository.findByPullRequestsIdAndHeadSha(pullRequestId, headCommitSha);
        var latestRuns = getLatestWorkflowRuns(runs);
        
        return latestRuns
                .map(WorkflowRunDTO::fromWorkflowRun)
                .toList();
    }

    public List<WorkflowRunDTO> getLatestWorkflowRunsByBranchAndHeadCommitSha(
        String branch,
        String headCommitSha
    ) {
        var runs = workflowRunRepository.findByHeadBranchAndHeadShaAndPullRequestsIsNull(branch, headCommitSha);
        var latestRuns = getLatestWorkflowRuns(runs);
        
        return latestRuns
                .map(WorkflowRunDTO::fromWorkflowRun)
                .toList();
    }
}