package de.tum.cit.aet.helios.deployment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @GetMapping
    public ResponseEntity<List<DeploymentDTO>> getAllDeployments() {
        List<DeploymentDTO> deployments = deploymentService.getAllDeployments();
        return ResponseEntity.ok(deployments);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeploymentDTO> getDeploymentById(@PathVariable Long id) {
        Optional<DeploymentDTO> deployment = deploymentService.getDeploymentById(id);
        return deployment.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/environment/{environmentId}")
    public ResponseEntity<List<DeploymentDTO>> getDeploymentsByEnvironmentId(@PathVariable Long environmentId) {
        List<DeploymentDTO> deployments = deploymentService.getDeploymentsByEnvironmentId(environmentId);
        return ResponseEntity.ok(deployments);
    }

    @GetMapping("/environment/{environmentId}/latest")
    public ResponseEntity<DeploymentDTO> getLatestDeploymentByEnvironmentId(@PathVariable Long environmentId) {
        Optional<DeploymentDTO> deployment = deploymentService.getLatestDeploymentByEnvironmentId(environmentId);
        return deployment.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}