package de.tum.cit.aet.helios.workflow.queue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueAlertEventRepository extends JpaRepository<QueueAlertEvent, Long> {

  Optional<QueueAlertEvent> findFirstByRuleIdAndClearedAtIsNull(Long ruleId);

  @Query(
      "SELECT e FROM QueueAlertEvent e WHERE "
          + "(:repoId IS NULL OR e.repositoryId = :repoId) "
          + "AND e.firedAt >= :since "
          + "ORDER BY e.firedAt DESC")
  List<QueueAlertEvent> findRecent(
      @Param("repoId") Long repositoryId, @Param("since") OffsetDateTime since);
}
