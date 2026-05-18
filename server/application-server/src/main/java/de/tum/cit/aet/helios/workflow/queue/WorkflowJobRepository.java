package de.tum.cit.aet.helios.workflow.queue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowJobRepository extends JpaRepository<WorkflowJob, Long> {

  List<WorkflowJob> findByRepositoryIdAndStatus(Long repositoryId, String status);

  List<WorkflowJob> findByRepositoryIdAndStatusInOrderByCreatedAtAsc(
      Long repositoryId, List<String> statuses);

  @Query(
      "SELECT j FROM WorkflowJob j WHERE j.status = 'queued' "
          + "AND j.createdAt < :before AND j.queuedReason IS NULL")
  List<WorkflowJob> findStuckCandidates(@Param("before") OffsetDateTime before);

  @Query(
      "SELECT j FROM WorkflowJob j "
          + "WHERE j.status IN ('queued','in_progress') "
          + "AND j.createdAt < :before "
          + "AND j.runnerId IS NULL "
          + "AND (j.lastReconcileAttemptAt IS NULL OR j.lastReconcileAttemptAt < :backoffBefore)")
  List<WorkflowJob> findJobsNeedingRunnerReconciliation(
      @Param("before") OffsetDateTime before,
      @Param("backoffBefore") OffsetDateTime backoffBefore);

  Optional<WorkflowJob> findByWorkflowRunIdAndName(Long workflowRunId, String name);

  @Modifying
  @Query(
      "UPDATE WorkflowJob j SET j.lastReconcileAttemptAt = :now WHERE j.id IN :ids")
  void touchReconcileAttempt(@Param("ids") List<Long> ids, @Param("now") OffsetDateTime now);
}
