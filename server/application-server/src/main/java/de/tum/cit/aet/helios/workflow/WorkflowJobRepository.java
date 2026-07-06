package de.tum.cit.aet.helios.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowJobRepository extends JpaRepository<WorkflowJob, Long> {

  List<WorkflowJob> findByWorkflowRunIdIn(Collection<Long> workflowRunIds);

  List<WorkflowJob> findByWorkflowRunId(Long workflowRunId);

  Optional<WorkflowJob> findByIdAndRepositoryRepositoryId(Long id, Long repositoryId);
}
