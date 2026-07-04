package de.tum.cit.aet.helios.workflow.queue;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueAlertRuleRepository extends JpaRepository<QueueAlertRule, Long> {

  List<QueueAlertRule> findByEnabledTrue();

  List<QueueAlertRule> findByRepositoryId(Long repositoryId);

  /** Scoped lookup so callers can't reach across repos by guessing ids. */
  Optional<QueueAlertRule> findByIdAndRepositoryId(Long id, Long repositoryId);

  /** Same, scoped delete. */
  long deleteByIdAndRepositoryId(Long id, Long repositoryId);
}
