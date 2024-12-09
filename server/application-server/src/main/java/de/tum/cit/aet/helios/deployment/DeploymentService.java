package de.tum.cit.aet.helios.deployment;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;

    public DeploymentService(DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
    }

    public Optional<DeploymentDTO> getDeploymentById(Long id) {
        return deploymentRepository.findById(id)
                .map(DeploymentDTO::fromDeployment);
    }

    public List<DeploymentDTO> getAllDeployments() {
        return deploymentRepository.findAll().stream()
                .map(DeploymentDTO::fromDeployment)
                .collect(Collectors.toList());
    }

    public List<DeploymentDTO> getDeploymentsByEnvironmentId(Long environmentId) {
        return deploymentRepository.findByEnvironmentEntityIdOrderByCreatedAtDesc(environmentId)
                .stream()
                .map(DeploymentDTO::fromDeployment)
                .collect(Collectors.toList());
    }


    public Optional<DeploymentDTO> getLatestDeploymentByEnvironmentId(Long environmentId) {
        return deploymentRepository.findFirstByEnvironmentEntityIdOrderByCreatedAtDesc(environmentId)
                .map(DeploymentDTO::fromDeployment);
    }

}