package de.tum.cit.aet.helios.deployment;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchService;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentDTO;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.github.GitHubService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final BranchService branchService;
    private final GitHubService gitHubService;
    private final EnvironmentService environmentService;

    public DeploymentService(
        DeploymentRepository deploymentRepository,
        GitHubService gitHubService,
        BranchService branchService,
        EnvironmentService environmentService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.gitHubService = gitHubService;
        this.environmentService = environmentService;
        this.branchService = branchService;
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
        return deploymentRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId)
                .stream()
                .map(DeploymentDTO::fromDeployment)
                .collect(Collectors.toList());
    }


    public Optional<DeploymentDTO> getLatestDeploymentByEnvironmentId(Long environmentId) {
        return deploymentRepository.findFirstByEnvironmentIdOrderByCreatedAtDesc(environmentId)
                .map(DeploymentDTO::fromDeployment);
    }

    public void deployToEnvironment(DeployRequest deployRequest) {
        EnvironmentDTO env = this.environmentService.getEnvironmentById(deployRequest.environmentId()).orElseThrow(
            () -> new DeploymentException("Environment not found")
        );

        Branch branch = this.branchService.getBranchByRepositoryIdAndName(
            env.repository().id(),
            deployRequest.branchName()
        ).orElseThrow(
            () -> new DeploymentException("Branch not found")
        );

        Environment environment = this.environmentService.lockEnvironment(deployRequest.environmentId(), branch).orElseThrow(
            () -> new DeploymentException("Environment was already locked")
        );

        try {
            this.gitHubService.dispatchWorkflow(
                environment.getRepository().getNameWithOwner(),
                "deploy.yml",
                deployRequest.branchName(),
                new HashMap<>()
            );
        } catch (IOException e) {
            // We want to make sure that the environment is unlocked in case of an error
            this.environmentService.unlockEnvironment(environment.getId());
            throw new DeploymentException("Failed to dispatch workflow due to IOException", e);
        }
    }
}