package de.tum.cit.aet.helios.workflow;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/workflows")
public class WorkflowRunController {

  private final WorkflowRunService workflowRunService;

  @GetMapping("/pr/{pullRequestId}")
  public ResponseEntity<List<WorkflowRunDto>> getLatestWorkflowRunsByPullRequestIdAndHeadCommit(
      @PathVariable Long pullRequestId) {
    var workflowRuns =
        workflowRunService.getLatestWorkflowRunsByPullRequestIdAndHeadCommit(pullRequestId);

    return ResponseEntity.ok(workflowRuns);
  }

  @GetMapping("/branch")
  public ResponseEntity<List<WorkflowRunDto>> getLatestWorkflowRunsByBranchAndHeadCommit(
      @RequestParam String branch) {
    var workflowRuns = workflowRunService.getLatestWorkflowRunsByBranchAndHeadCommitSha(branch);
    return ResponseEntity.ok(workflowRuns);
  }
}
