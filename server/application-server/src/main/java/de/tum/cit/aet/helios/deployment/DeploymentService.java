package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowService;
import de.tum.cit.aet.helios.github.GitHubService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DeploymentService {

  private final DeploymentRepository deploymentRepository;
  private final GitHubService gitHubService;
  private final EnvironmentService environmentService;
  private final WorkflowService workflowService;

  public DeploymentService(
      DeploymentRepository deploymentRepository,
      GitHubService gitHubService,
      EnvironmentService environmentService,
      WorkflowService workflowService) {
    this.deploymentRepository = deploymentRepository;
    this.gitHubService = gitHubService;
    this.environmentService = environmentService;
    this.workflowService = workflowService;
  }

  public Optional<DeploymentDto> getDeploymentById(Long id) {
    return deploymentRepository.findById(id).map(DeploymentDto::fromDeployment);
  }

  public List<DeploymentDto> getAllDeployments() {
    return deploymentRepository.findAll().stream()
        .map(DeploymentDto::fromDeployment)
        .collect(Collectors.toList());
  }

  public List<DeploymentDto> getDeploymentsByEnvironmentId(Long environmentId) {
    return deploymentRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId).stream()
        .map(DeploymentDto::fromDeployment)
        .collect(Collectors.toList());
  }

  public Optional<DeploymentDto> getLatestDeploymentByEnvironmentId(Long environmentId) {
    return deploymentRepository
        .findFirstByEnvironmentIdOrderByCreatedAtDesc(environmentId)
        .map(DeploymentDto::fromDeployment);
  }

  public void deployToEnvironment(DeployRequest deployRequest) {
    Environment environment =
        this.environmentService
            .lockEnvironment(deployRequest.environmentId())
            .orElseThrow(() -> new DeploymentException("Environment was already locked"));

    try {
      //Get the deployment workflow set by the managers
      Workflow deploymentWorkflow = this.workflowService.getDeploymentWorkflow();
      if (deploymentWorkflow == null) {
        this.environmentService.unlockEnvironment(environment.getId());
        throw new DeploymentException("No deployment workflow found");
      }
      this.gitHubService.dispatchWorkflow(
          environment.getRepository().getNameWithOwner(),
          deploymentWorkflow.getFileNameWithExtension(),
          deployRequest.branchName(),
          new HashMap<>());
    } catch (IOException e) {
      // We want to make sure that the environment is unlocked in case of an error
      this.environmentService.unlockEnvironment(environment.getId());
      throw new DeploymentException("Failed to dispatch workflow due to IOException", e);
    }
  }
}
