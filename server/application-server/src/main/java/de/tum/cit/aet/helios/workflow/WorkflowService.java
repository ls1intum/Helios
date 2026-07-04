package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.filters.RepositoryContext;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkflowService {
  private final WorkflowRepository workflowRepository;

  @Transactional(readOnly = true)
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
        ? Optional.empty()
        : workflowRepository.findByIdAndRepositoryRepositoryId(id, repositoryId);
  }

  @Transactional(readOnly = true)
  public List<WorkflowDto> getAllWorkflows() {
    Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null) {
      return List.of();
    }
    return workflowRepository
        .findByRepositoryRepositoryIdOrderByCreatedAtDesc(repositoryId)
        .stream()
        .map(WorkflowDto::fromWorkflow)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<WorkflowDto> getWorkflowsByRepositoryId(Long repositoryId) {
    return workflowRepository
        .findByRepositoryRepositoryIdOrderByCreatedAtDesc(repositoryId)
        .stream()
        .map(WorkflowDto::fromWorkflow)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
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

  @Transactional(readOnly = true)
  public List<Workflow> getDeploymentWorkflowsForAllEnv(Long repositoryId) {
    return workflowRepository.findDeploymentWorkflowsForEnabledEnvironmentsByRepositoryId(
        repositoryId);
  }

  @Transactional(readOnly = true)
  public List<Workflow> getTestWorkflows(Long repositoryId) {
    return workflowRepository.findByLabelAndRepositoryRepositoryId(
        Workflow.Label.TEST, repositoryId);
  }
}
