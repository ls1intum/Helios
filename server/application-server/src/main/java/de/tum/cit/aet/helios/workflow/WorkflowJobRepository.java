package de.tum.cit.aet.helios.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowJobRepository extends JpaRepository<WorkflowJob, Long> {

  List<WorkflowJob> findByWorkflowRunIdIn(Collection<Long> workflowRunIds);

  List<WorkflowJob> findByWorkflowRunId(Long workflowRunId);

  Optional<WorkflowJob> findByIdAndRepositoryRepositoryId(Long id, Long repositoryId);

  /** Distinct observed job names for a repository — the source for pipeline auto-detection. */
  @Query(
      "SELECT DISTINCT j.name FROM WorkflowJob j "
          + "WHERE j.repository.repositoryId = :repositoryId AND j.name IS NOT NULL")
  List<String> findDistinctJobNamesByRepositoryId(@Param("repositoryId") Long repositoryId);
}
