package de.tum.cit.aet.helios.environment.protectionrules;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtectionRuleRepository extends JpaRepository<ProtectionRule, Long> {
  List<ProtectionRule> findByEnvironmentId(Long environmentId);

  Optional<ProtectionRule> findByEnvironmentIdAndRuleType(
      Long environmentId, ProtectionRule.RuleType ruleType);

  boolean existsByEnvironmentIdAndRuleType(Long environmentId, ProtectionRule.RuleType ruleType);

  void deleteByEnvironmentIdAndRuleType(Long environmentId, ProtectionRule.RuleType ruleType);

  List<ProtectionRule> findByEnvironmentIdAndProtectedBranchesTrue(Long environmentId);

  List<ProtectionRule> findByEnvironmentIdAndWaitTimerNotNull(Long environmentId);

  List<ProtectionRule> findByEnvironmentIdAndPreventSelfReviewTrue(Long environmentId);
}
