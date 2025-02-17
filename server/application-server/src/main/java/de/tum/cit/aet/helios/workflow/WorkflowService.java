package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class WorkflowService {

  private final WorkflowRepository workflowRepository;
  private final EnvironmentService environmentService;

  public WorkflowService(
      WorkflowRepository workflowRepository, EnvironmentService environmentService) {
    this.workflowRepository = workflowRepository;
    this.environmentService = environmentService;
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

  public void updateWorkflowLabel(Long workflowId, Workflow.Label label) {
    Workflow workflow =
        workflowRepository
            .findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found!"));
    workflow.setLabel(label);
    workflowRepository.save(workflow);
  }

  public Workflow getDeploymentWorkflowByLabel(Workflow.Label label, Long repositoryId) {
    Workflow workflow =
        workflowRepository.findFirstByLabelAndRepositoryRepositoryIdOrderByCreatedAtDesc(
            label, repositoryId);
    return workflow;
  }

  public List<Workflow> getDeploymentWorkflowsForAllEnv(Long repositoryId) {

    return Arrays.stream(Workflow.Label.values())
        .filter(
            label ->
                label == Workflow.Label.DEPLOY_TEST_SERVER
                    || label == Workflow.Label.DEPLOY_STAGING_SERVER
                    || label == Workflow.Label.DEPLOY_PRODUCTION_SERVER)
        .map(
            label ->
                workflowRepository.findFirstByLabelAndRepositoryRepositoryIdOrderByCreatedAtDesc(
                    label, repositoryId))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public Workflow.Label getDeploymentWorkflowLabelForEnvType(Environment.Type type) {
    switch (type) {
      case PRODUCTION -> {
        return Workflow.Label.DEPLOY_PRODUCTION_SERVER;
      }
      case STAGING -> {
        return Workflow.Label.DEPLOY_STAGING_SERVER;
      }
      case TEST -> {
        return Workflow.Label.DEPLOY_TEST_SERVER;
      }
      default -> {
        return null;
      }
    }
  }

  public Workflow getDeploymentWorkflowForEnv(Long environmentId) {
    Environment.Type environmentType =
        this.environmentService
            .getEnvironmentTypeById(environmentId)
            .orElseThrow(() -> new EntityNotFoundException("Environment not found"));

    Workflow.Label label = this.getDeploymentWorkflowLabelForEnvType(environmentType);
    if (label == null) {
      throw new IllegalStateException("No workflow for this deployment environment found");
    }

    Workflow deploymentWorkflow =
        this.getDeploymentWorkflowByLabel(label, RepositoryContext.getRepositoryId());
    if (deploymentWorkflow == null) {
      throw new NoSuchElementException("No deployment workflow found");
    }
    return deploymentWorkflow;
  }

  public List<Workflow> getTestWorkflows(Long repositoryId) {
    return workflowRepository.findByLabelAndRepositoryRepositoryId(
        Workflow.Label.TEST, repositoryId);
  }
}
