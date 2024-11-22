package de.tum.cit.aet.helios.deployment.deployments;

import java.util.List;

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
    public ResponseEntity<List<DeploymentDTO>> getAllDeployments() {
        List<DeploymentDTO> deployments = deploymentService.getAllDeployments();
        if (deployments.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(deployments);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeploymentDTO> getDeploymentById(@PathVariable Long id) {
        return deploymentService.getDeploymentById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/branch/{branchName}")
    public ResponseEntity<List<DeploymentDTO>> getDeploymentsByBranch(@PathVariable String branchName) {
        List<DeploymentDTO> deployments = deploymentService.getDeploymentsByBranch(branchName);
        if (deployments.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(deployments);
        }
    }

    @PostMapping()
    public ResponseEntity<DeploymentDTO> postMethodName(@RequestBody DeploymentDTO endeploymentDto) {
        DeploymentDTO createdDeployment = deploymentService.createDeployment(endeploymentDto);
    return ResponseEntity.ok(createdDeployment);
    }
    
    
}