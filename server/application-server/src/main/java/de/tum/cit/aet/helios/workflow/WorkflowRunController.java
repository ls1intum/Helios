package de.tum.cit.aet.helios.workflow;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowRunController {

  @Autowired private WorkflowRunService workflowRunService;

  @GetMapping("/pr/{pullRequestId}")
  public ResponseEntity<List<WorkflowRunDTO>> getLatestWorkflowRunsByPullRequestIdAndHeadCommit(
      @PathVariable Long pullRequestId) {
    var workflowRuns =
        workflowRunService.getLatestWorkflowRunsByPullRequestIdAndHeadCommit(pullRequestId);

    return ResponseEntity.ok(workflowRuns);
  }

  @GetMapping("/branch/{branch}")
  public ResponseEntity<List<WorkflowRunDTO>> getLatestWorkflowRunsByBranchAndHeadCommit(
      @PathVariable String branch) {
    var workflowRuns = workflowRunService.getLatestWorkflowRunsByBranchAndHeadCommitSha(branch);

    return ResponseEntity.ok(workflowRuns);
  }
}
