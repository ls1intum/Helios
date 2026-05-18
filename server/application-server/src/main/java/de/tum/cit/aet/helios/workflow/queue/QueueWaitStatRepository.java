package de.tum.cit.aet.helios.workflow.queue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueWaitStatRepository extends JpaRepository<QueueWaitStat, Long> {

  @Query(
      "SELECT s FROM QueueWaitStat s WHERE s.repositoryId = :repoId "
          + "AND (:workflowName IS NULL OR s.workflowName = :workflowName) "
          + "AND (:jobName IS NULL OR s.jobName = :jobName) "
          + "AND (:headBranch IS NULL OR s.headBranch = :headBranch) "
          + "AND s.bucketStart >= :since "
          + "ORDER BY s.bucketStart ASC")
  List<QueueWaitStat> findForWindow(
      @Param("repoId") Long repositoryId,
      @Param("workflowName") String workflowName,
      @Param("jobName") String jobName,
      @Param("headBranch") String headBranch,
      @Param("since") OffsetDateTime since);

  Optional<QueueWaitStat>
      findFirstByRepositoryIdAndWorkflowNameAndJobNameAndHeadBranchAndLabelSetHashAndBucketStart(
          Long repositoryId,
          String workflowName,
          String jobName,
          String headBranch,
          String labelSetHash,
          OffsetDateTime bucketStart);
}
