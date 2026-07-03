package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.filters.RepositoryContext;
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
    return findScopedById(id).map(WorkflowDto::fromWorkflow);
  }

  /**
   * Loads a workflow by id, scoped to the current repository when a repository context is present.
   * Explicit replacement for the ambient gitRepositoryFilter, which never applied to findById/PK
   * loads — so this also closes a latent cross-repository read.
   */
  private Optional<Workflow> findScopedById(Long id) {
    Long repositoryId = RepositoryContext.getRepositoryId();
    return repositoryId == null
        ? workflowRepository.findById(id)
        : workflowRepository.findByIdAndRepositoryRepositoryId(id, repositoryId);
  }

  public List<WorkflowDto> getAllWorkflows() {
    Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null) {
      return List.of();
    }
    return workflowRepository.findByRepositoryRepositoryIdOrderByCreatedAtDesc(repositoryId).stream()
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
    Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null) {
      return List.of();
    }
    return workflowRepository
        .findByStateAndRepositoryRepositoryIdOrderByCreatedAtDesc(state, repositoryId)
        .stream()
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
