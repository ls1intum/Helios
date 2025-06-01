package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastWritePermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

  private final DeploymentService deploymentService;

  @GetMapping
  public ResponseEntity<List<DeploymentDto>> getAllDeployments() {
    List<DeploymentDto> deployments = deploymentService.getAllDeployments();
    return ResponseEntity.ok(deployments);
  }

  @GetMapping("/{id}")
  public ResponseEntity<DeploymentDto> getDeploymentById(@PathVariable Long id) {
    Optional<DeploymentDto> deployment = deploymentService.getDeploymentById(id);
    return deployment.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/environment/{environmentId}")
  public ResponseEntity<List<DeploymentDto>> getDeploymentsByEnvironmentId(
      @PathVariable Long environmentId) {
    List<DeploymentDto> deployments =
        deploymentService.getDeploymentsByEnvironmentId(environmentId);
    return ResponseEntity.ok(deployments);
  }

  @GetMapping("/environment/{environmentId}/latest")
  public ResponseEntity<DeploymentDto> getLatestDeploymentByEnvironmentId(
      @PathVariable Long environmentId) {
    Optional<DeploymentDto> deployment =
        deploymentService.getLatestDeploymentByEnvironmentId(environmentId);
    return deployment.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @EnforceAtLeastWritePermission
  @PostMapping("/deploy")
  public ResponseEntity<String> deployToEnvironment(
      @Valid @RequestBody DeployRequest deployRequest) {
    deploymentService.deployToEnvironment(deployRequest);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/environment/{environmentId}/activity-history")
  public ResponseEntity<List<ActivityHistoryDto>> getActivityHistoryByEnvironmentId(
      @PathVariable Long environmentId) {
    List<ActivityHistoryDto> activityHistory =
        deploymentService.getActivityHistoryByEnvironmentId(environmentId);
    return ResponseEntity.ok(activityHistory);
  }

  /**
   * Cancels an ongoing deployment.
   *
   * @param cancelRequest The request containing the workflow run ID to cancel
   * @return ResponseEntity with a success message
   */
  @EnforceAtLeastWritePermission
  @PostMapping("/cancel")
  public ResponseEntity<String> cancelDeployment(
      @Valid @RequestBody CancelDeploymentRequest cancelRequest) {
    String result = deploymentService.cancelDeployment(cancelRequest);
    return ResponseEntity.ok(result);
  }

  @EnforceAtLeastWritePermission
  @GetMapping("/workflowJobStatus/{runId}")
  public ResponseEntity<WorkflowJobsResponse> getWorkflowJobStatus(@PathVariable Long runId) {
    WorkflowJobsResponse jobStatusResponse = deploymentService.getWorkflowJobStatus(runId);
    return ResponseEntity.ok(jobStatusResponse);
  }
}
