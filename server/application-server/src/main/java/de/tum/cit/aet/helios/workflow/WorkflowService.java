package de.tum.cit.aet.helios.workflow;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowService {
  private final WorkflowRepository workflowRepository;

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
    return workflowRepository.findDeploymentWorkflowsForEnabledEnvironmentsByRepositoryId(
        repositoryId);
  }

  public List<Workflow> getTestWorkflows(Long repositoryId) {
    return workflowRepository.findByLabelAndRepositoryRepositoryId(
        Workflow.Label.TEST, repositoryId);
  }
}
