package de.tum.cit.aet.helios.environment.protectionrules;

import de.tum.cit.aet.helios.environment.Environment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "environment_protection_rule",
    uniqueConstraints = {
      @UniqueConstraint(
          columnNames = {"environment_id", "rule_type"},
          name = "unique_rule_per_env")
    })
@Getter
@Setter
@RequiredArgsConstructor
@ToString(exclude = "environment")
public class ProtectionRule {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "environment_id", nullable = false)
  private Environment environment;

  @Enumerated(EnumType.STRING)
  @Column(name = "rule_type", nullable = false, length = 50)
  private RuleType ruleType;

  @Column(name = "prevent_self_review")
  private Boolean preventSelfReview;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reviewers", columnDefinition = "jsonb")
  private String reviewers;

  @Column(name = "wait_timer")
  private Integer waitTimer;

  @Column(name = "protected_branches")
  private Boolean protectedBranches;

  @Column(name = "custom_branch_policies")
  private Boolean customBranchPolicies;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "allowed_branches", columnDefinition = "jsonb")
  private String allowedBranches;

  @Column(name = "created_at", nullable = false, updatable = false)
  private ZonedDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;

  // Enum for rule types
  public enum RuleType {
    WAIT_TIMER,
    REQUIRED_REVIEWERS,
    BRANCH_POLICY
  }

  @PrePersist
  protected void onCreate() {
    createdAt = ZonedDateTime.now();
    updatedAt = ZonedDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = ZonedDateTime.now();
  }
}
