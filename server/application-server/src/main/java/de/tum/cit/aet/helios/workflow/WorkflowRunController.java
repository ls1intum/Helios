package de.tum.cit.aet.helios.workflow;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowRunController {

    @Autowired
    private WorkflowRunService workflowRunService;

    @GetMapping("/pr/{pullRequestId}/commit/{commitSha}")
    public ResponseEntity<List<WorkflowRunDTO>> getLatestWorkflowRunsByPullRequestIdAndHeadCommitSha(
        @PathVariable Long pullRequestId,
        @PathVariable String commitSha
    ) {
        var workflowRuns = workflowRunService.getLatestWorkflowRunsByPullRequestIdAndHeadCommitSha(
            pullRequestId,    
            commitSha
        );

        return ResponseEntity.ok(workflowRuns);
    }

    @GetMapping("/branch/{branch}/commit/{commitSha}")
    public ResponseEntity<List<WorkflowRunDTO>> getLatestWorkflowRunsByBranchAndHeadCommitSha(
        @PathVariable String branch,
        @PathVariable String commitSha
    ) {
        var workflowRuns = workflowRunService.getLatestWorkflowRunsByBranchAndHeadCommitSha(
            branch,
            commitSha
        );

        return ResponseEntity.ok(workflowRuns);
    }
}