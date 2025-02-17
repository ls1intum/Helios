package de.tum.cit.aet.helios.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowRunController {

  @Autowired private WorkflowRunService workflowRunService;

  @GetMapping("/pr/{pullRequestId}")
  public ResponseEntity<List<WorkflowRunDto>> getLatestWorkflowRunsByPullRequestIdAndHeadCommit(
      @PathVariable Long pullRequestId,
      @RequestParam(defaultValue = "false") boolean includeTestSuites) {
    var workflowRuns =
        workflowRunService.getLatestWorkflowRunsByPullRequestIdAndHeadCommit(
            pullRequestId, includeTestSuites);

    return ResponseEntity.ok(workflowRuns);
  }

  @GetMapping("/branch")
  public ResponseEntity<List<WorkflowRunDto>> getLatestWorkflowRunsByBranchAndHeadCommit(
      @RequestParam String branch,
      @RequestParam(defaultValue = "false") boolean includeTestSuites) {
    var workflowRuns =
        workflowRunService.getLatestWorkflowRunsByBranchAndHeadCommitSha(branch, includeTestSuites);
    return ResponseEntity.ok(workflowRuns);
  }

  @GetMapping("/runs/{environmentId}")
  public ResponseEntity<Optional<String>> getWorkflowRunUrl(
      @PathVariable Long environmentId,
      @RequestParam String branch,
      @RequestParam String commitSha) {
    String getWorkflowRunUrl =
        workflowRunService.getWorkflowRunUrl(branch, commitSha, environmentId);
    return ResponseEntity.ok(Optional.ofNullable(getWorkflowRunUrl));
  }
}
