package de.tum.cit.aet.helios.deployment;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

  private final DeploymentService deploymentService;

  public DeploymentController(DeploymentService deploymentService) {
    this.deploymentService = deploymentService;
  }

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

  @PostMapping("/deploy")
  public ResponseEntity<String> deployToEnvironment(
      @Valid @RequestBody DeployRequest deployRequest) {
    deploymentService.deployToEnvironment(deployRequest);
    return ResponseEntity.ok().build();
  }
}
