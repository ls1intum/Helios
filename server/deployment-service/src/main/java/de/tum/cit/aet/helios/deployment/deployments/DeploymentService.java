package de.tum.cit.aet.helios.deployment.deployments;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;

    @Autowired
    public DeploymentService(DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
    }

    public List<DeploymentDTO> getAllDeployments() {
        return deploymentRepository.findAll().stream()
                .map(DeploymentDTO::fromDeployment)
                .collect(Collectors.toList());
    }

    public Optional<DeploymentDTO> getDeploymentById(Long id) {
        return deploymentRepository.findById(id)
                .map(DeploymentDTO::fromDeployment);
    }

    public List<DeploymentDTO> getDeploymentsByBranch(String branchName) {
        return deploymentRepository.findAllByBranchName(branchName).stream()
                .map(DeploymentDTO::fromDeployment)
                .collect(Collectors.toList());
    }

    public DeploymentDTO createDeployment(DeploymentDTO deploymentDTO) {
        Deployment deployment = new Deployment(deploymentDTO);
        return DeploymentDTO.fromDeployment(deploymentRepository.save(deployment));
    }
}