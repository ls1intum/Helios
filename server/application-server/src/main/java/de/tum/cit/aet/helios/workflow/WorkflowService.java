package de.tum.cit.aet.helios.workflow;

import jakarta.transaction.Transactional;
import java.util.List;
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

  public void updateWorkflowLabel(Long workflowId, Workflow.Label label) {
    Workflow workflow =
        workflowRepository
            .findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found!"));
    workflow.setLabel(label);
    workflowRepository.save(workflow);
  }

  public Workflow getDeploymentWorkflow(Long repositoryId) {
    Workflow workflow =
        workflowRepository.findFirstByLabelAndRepositoryRepositoryIdOrderByCreatedAtDesc(
            Workflow.Label.DEPLOYMENT, repositoryId);
    return workflow;
  }

  public Workflow getTestWorkflow(Long repositoryId) {
    Workflow workflow =
        workflowRepository.findFirstByLabelAndRepositoryRepositoryIdOrderByCreatedAtDesc(
            Workflow.Label.TEST, repositoryId);
    return workflow;
  }
}
