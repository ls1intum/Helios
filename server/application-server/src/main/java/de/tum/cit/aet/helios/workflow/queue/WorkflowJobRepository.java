package de.tum.cit.aet.helios.workflow.queue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowJobRepository extends JpaRepository<WorkflowJob, Long> {

  List<WorkflowJob> findByRepositoryIdAndStatus(Long repositoryId, String status);

  List<WorkflowJob> findByRepositoryIdAndStatusInOrderByCreatedAtAsc(
      Long repositoryId, List<String> statuses);

  /** Paginated variant — pushes LIMIT into SQL instead of loading the whole result set. */
  List<WorkflowJob> findByRepositoryIdAndStatusInOrderByCreatedAtAsc(
      Long repositoryId, List<String> statuses, Pageable pageable);

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

  /** Recent completed jobs in a repo, newest first; bounded by JPA pagination. */
  List<WorkflowJob>
      findTop50ByRepositoryIdAndStatusAndRunDurationSecondsNotNullOrderByCompletedAtDesc(
          Long repositoryId, String status);

  /** Org-wide queued/in-progress jobs. Bounded; for org-depth dashboard. */
  List<WorkflowJob> findByStatusInOrderByCreatedAtAsc(List<String> statuses);

  /** Currently-stuck queued jobs, optionally scoped to a repo or label-set. */
  @Query(
      "SELECT COUNT(j) FROM WorkflowJob j WHERE j.isStuck = true AND j.status = 'queued' "
          + "AND (:repoId IS NULL OR j.repositoryId = :repoId) "
          + "AND (:labelSetHash IS NULL OR j.labelSetHash = :labelSetHash)")
  long countCurrentlyStuck(
      @Param("repoId") Long repositoryId,
      @Param("labelSetHash") String labelSetHash);
}
