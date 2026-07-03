package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.Workflow.Label;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
  List<Workflow> findByRepository(GitRepository repository);

  List<Workflow> findByRepositoryRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

  List<Workflow> findByStateOrderByCreatedAtDesc(Workflow.State state);

  // Explicit per-repository scoped finders (Option B).
  List<Workflow> findByStateAndRepositoryRepositoryIdOrderByCreatedAtDesc(
      Workflow.State state, Long repositoryId);

  Optional<Workflow> findByIdAndRepositoryRepositoryId(Long id, Long repositoryId);

  List<Workflow> findByLabelAndRepositoryRepositoryId(Label label, Long repositoryId);

  Workflow findFirstByLabelAndRepositoryRepositoryIdOrderByCreatedAtDesc(
      Label label, Long repositoryId);

  @Query(
      "SELECT DISTINCT e.deploymentWorkflow FROM Environment e "
          + "WHERE e.enabled = true "
          + "AND e.repository.repositoryId = :repositoryId "
          + "AND e.deploymentWorkflow IS NOT NULL")
  List<Workflow> findDeploymentWorkflowsForEnabledEnvironmentsByRepositoryId(
      @Param("repositoryId") Long repositoryId);
}
