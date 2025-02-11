package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.environment.Environment;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class WorkflowService {

  private final WorkflowRepository workflowRepository;

  public WorkflowService(WorkflowRepository workflowRepository) {
    this.workflowRepository = workflowRepository;
  }

  public Optional<WorkflowDto> getWorkflowById(Long id) {
    return workflowRepository.findById(id).map(WorkflowDto::fromWorkflow);
  }

  public List<WorkflowDto> getAllWorkflows() {
    return workflowRepository.findAll().stream()
        .map(WorkflowDto::fromWorkflow)
        .collect(Collectors.toList());
  }

  public List<WorkflowDto> getWorkflowsByRepositoryId(Long repositoryId) {
    return workflowRepository
        .findByRepositoryRepositoryIdOrderByCreatedAtDesc(repositoryId)
        .stream()
        .map(WorkflowDto::fromWorkflow)
        .collect(Collectors.toList());
  }

  public List<WorkflowDto> getWorkflowsByState(Workflow.State state) {
    return workflowRepository.findByStateOrderByCreatedAtDesc(state).stream()
        .map(WorkflowDto::fromWorkflow)
        .collect(Collectors.toList());
  }

  public void updateWorkflowDeploymentEnvironment(
      Long workflowId, Workflow.DeploymentEnvironment env) {
    Workflow workflow =
        workflowRepository
            .findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found!"));
    workflow.setDeploymentEnvironment(env);
    workflowRepository.save(workflow);
  }

  public Workflow getDeploymentWorkflowByEnvironment(
      Workflow.DeploymentEnvironment environment, Long repositoryId) {
    Workflow workflow =
        workflowRepository
            .findFirstByRepositoryRepositoryIdAndDeploymentEnvironmentOrderByCreatedAtDesc(
                repositoryId, environment);
    return workflow;
  }

  public List<Workflow> getDeploymentWorkflowsForAllEnv(Long repositoryId) {

    return Arrays.stream(Workflow.DeploymentEnvironment.values())
        .filter(env -> env != Workflow.DeploymentEnvironment.NONE)
        .map(
            env ->
                workflowRepository
                    .findFirstByRepositoryRepositoryIdAndDeploymentEnvironmentOrderByCreatedAtDesc(
                        repositoryId, env))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public Workflow.DeploymentEnvironment getDeploymentEnvironment(Environment.Type type) {
    switch (type) {
      case PRODUCTION -> {
        return Workflow.DeploymentEnvironment.PRODUCTION_SERVER;
      }
      case STAGING -> {
        return Workflow.DeploymentEnvironment.STAGING_SERVER;
      }
      case TEST -> {
        return Workflow.DeploymentEnvironment.TEST_SERVER;
      }
      default -> {
        return Workflow.DeploymentEnvironment.NONE;
      }
    }
  }
}
