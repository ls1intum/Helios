package de.tum.cit.aet.helios.environment.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.protectionrules.ProtectionRule;
import de.tum.cit.aet.helios.environment.protectionrules.ProtectionRuleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class GitHubEnvironmentConverter implements Converter<GitHubEnvironmentDto, Environment> {

  private final ProtectionRuleRepository protectionRuleRepository;
  private final ObjectMapper objectMapper;

  @Override
  public Environment convert(@NonNull GitHubEnvironmentDto source) {
    return update(source, new Environment());
  }

  public Environment update(
      @NonNull GitHubEnvironmentDto source, @NonNull Environment environment) {
    environment.setId(source.getId());
    environment.setName(source.getName());
    environment.setUrl(source.getUrl());
    environment.setHtmlUrl(source.getHtmlUrl());
    environment.setCreatedAt(source.getCreatedAt());
    environment.setUpdatedAt(source.getUpdatedAt());

    // The repository field will be set separately in the sync service
    return environment;
  }

  public void updateProtectionRules(
      @NonNull GitHubEnvironmentDto source, @NonNull Environment environment) {

    // Get all existing protection rules for this environment
    List<ProtectionRule> existingRules =
        protectionRuleRepository.findByEnvironmentId(environment.getId());

    // Create a set to track which rules we process from GitHub
    Set<Long> processedRuleIds = new HashSet<>();

    // Process protection rules from GitHub
    if (source.getProtectionRules() != null) {
      for (GitHubEnvironmentProtectionRuleDto ruleDto : source.getProtectionRules()) {
        ProtectionRule rule =
            protectionRuleRepository
                .findByEnvironmentIdAndRuleType(environment.getId(), mapRuleType(ruleDto.getType()))
                .orElseGet(
                    () -> {
                      ProtectionRule newRule = new ProtectionRule();
                      newRule.setEnvironment(environment);
                      newRule.setRuleType(mapRuleType(ruleDto.getType()));
                      return newRule;
                    });

        rule.setId(ruleDto.getId());
        switch (rule.getRuleType()) {
          case WAIT_TIMER -> rule.setWaitTimer(ruleDto.getWaitTimer());
          case REQUIRED_REVIEWERS -> {
            rule.setPreventSelfReview(ruleDto.getPreventSelfReview());
            try {
              rule.setReviewers(objectMapper.writeValueAsString(ruleDto.getReviewers()));
            } catch (JsonProcessingException e) {
              throw new RuntimeException("Failed to serialize reviewers", e);
            }
          }
          case BRANCH_POLICY -> {
            var branchPolicy = ruleDto.getBranchPolicy();
            if (branchPolicy != null) {
              rule.setProtectedBranches(branchPolicy.getProtectedBranches());
              rule.setCustomBranchPolicies(branchPolicy.getCustomBranchPolicies());
              try {
                rule.setAllowedBranches(
                    objectMapper.writeValueAsString(branchPolicy.getAllowedBranches()));
              } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize allowed branches", e);
              }
            } else {
              // Handle null branchPolicy with default values
              log.warn("Null branchPolicy found for rule ID: {}", rule.getId());
              rule.setProtectedBranches(false);
              rule.setCustomBranchPolicies(false);
              rule.setAllowedBranches("[]"); // Empty JSON array
            }
          }
          default -> throw new IllegalArgumentException("Unknown rule type: " + rule.getRuleType());
        }

        // Save the rule and track its ID
        rule = protectionRuleRepository.save(rule);
        if (rule.getId() != null) {
          processedRuleIds.add(rule.getId());
        }
      }
    }

    // Delete any existing rules that weren't in the GitHub data
    for (ProtectionRule existingRule : existingRules) {
      if (existingRule.getId() != null && !processedRuleIds.contains(existingRule.getId())) {
        protectionRuleRepository.delete(existingRule);
      }
    }
  }

  private ProtectionRule.RuleType mapRuleType(String githubType) {
    return switch (githubType.toLowerCase()) {
      case "wait_timer" -> ProtectionRule.RuleType.WAIT_TIMER;
      case "required_reviewers" -> ProtectionRule.RuleType.REQUIRED_REVIEWERS;
      case "branch_policy" -> ProtectionRule.RuleType.BRANCH_POLICY;
      default -> throw new IllegalArgumentException("Unknown rule type: " + githubType);
    };
  }
}
