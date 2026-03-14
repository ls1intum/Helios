package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastWritePermission;
import de.tum.cit.aet.helios.workflow.logs.WorkflowRunLogReaderService;
import de.tum.cit.aet.helios.workflow.logs.WorkflowRunLogsResponse;
import de.tum.cit.aet.helios.workflow.pagination.PaginatedWorkflowRunsResponse;
import de.tum.cit.aet.helios.workflow.pagination.WorkflowRunFilterType;
import de.tum.cit.aet.helios.workflow.pagination.WorkflowRunPageRequest;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/workflows")
public class WorkflowRunController {

  private final WorkflowRunService workflowRunService;
  private final WorkflowRunLogReaderService workflowRunLogReaderService;

  @GetMapping("/runs")
  public ResponseEntity<PaginatedWorkflowRunsResponse> getWorkflowRuns(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sortField,
      @RequestParam(required = false) String sortDirection,
      @RequestParam(required = false) WorkflowRunFilterType filterType,
      @RequestParam(required = false) String searchTerm) {
    WorkflowRunPageRequest pageRequest = WorkflowRunPageRequest.builder()
        .page(page)
        .size(size)
        .sortField(sortField)
        .sortDirection(sortDirection)
        .filterType(filterType != null ? filterType : WorkflowRunFilterType.ALL)
        .searchTerm(searchTerm)
        .build();
    return ResponseEntity.ok(workflowRunService.getPaginatedWorkflowRuns(pageRequest));
  }

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

  @GetMapping("/runs/{runId}")
  public ResponseEntity<WorkflowRunDto> getWorkflowRunById(@PathVariable Long runId) {
    return ResponseEntity.ok(workflowRunService.getWorkflowRunById(runId));
  }

  @EnforceAtLeastWritePermission
  @PostMapping("/runs/{runId}/cancel")
  public ResponseEntity<Void> cancelWorkflowRun(@PathVariable Long runId) {
    workflowRunService.cancelWorkflowRun(runId);
    return ResponseEntity.ok().build();
  }

  @EnforceAtLeastWritePermission
  @PostMapping("/runs/{runId}/rerun")
  public ResponseEntity<Void> reRunWorkflow(@PathVariable Long runId) {
    workflowRunService.reRunWorkflow(runId);
    return ResponseEntity.ok().build();
  }

  @EnforceAtLeastWritePermission
  @PostMapping("/runs/{runId}/rerun-failed-jobs")
  public ResponseEntity<Void> reRunFailedJobs(@PathVariable Long runId) {
    workflowRunService.reRunFailedJobs(runId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/runs/{workflowRunId}/logs")
  public ResponseEntity<WorkflowRunLogsResponse> getWorkflowRunLogs(
      @PathVariable Long workflowRunId) {
    try {
      return ResponseEntity.ok(workflowRunLogReaderService.getLogs(workflowRunId));
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load workflow logs", e);
    }
  }
}
