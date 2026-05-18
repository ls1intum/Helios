package de.tum.cit.aet.helios.workflow.queue;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueAlertRuleRepository extends JpaRepository<QueueAlertRule, Long> {

  List<QueueAlertRule> findByEnabledTrue();

  List<QueueAlertRule> findByRepositoryId(Long repositoryId);
}
