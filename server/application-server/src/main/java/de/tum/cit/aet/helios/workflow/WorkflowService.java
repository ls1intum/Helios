package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.environment.EnvironmentService;
import jakarta.transaction.Transactional;
import java.util.List;
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

  public List<Workflow> getDeploymentWorkflowsForAllEnv(Long repositoryId) {
    return environmentService.getAllEnabledEnvironments().stream()
        .filter(env -> env.repository().id().equals(repositoryId))
        .filter(env -> env.deploymentWorkflow() != null)
        .map(env -> env.deploymentWorkflow().id())
        .distinct()
        .map(workflowId -> workflowRepository.findById(workflowId))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  public List<Workflow> getTestWorkflows(Long repositoryId) {
    return workflowRepository.findByLabelAndRepositoryRepositoryId(
        Workflow.Label.TEST, repositoryId);
  }
}
