package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Precomputed flakiness metric for test cases, stored in a separate table for efficient retrieval.
 *
 * <p>One row per unique (repository, test_name, class_name, test_suite_name) combination.
 * Updated whenever new test statistics are computed, allowing for quick access to flakiness scores
 * without needing to recompute them on the fly.
 */
@Entity
@Table(
    name = "test_case_flakiness",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_test_case_flakiness",
          columnNames = {"repository_id", "test_name", "class_name", "test_suite_name"})
    },
    indexes = {
      @Index(name = "idx_flakiness_repo_score", columnList = "repository_id,flakiness_score DESC")
    })
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TestCaseFlakiness {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "repository_id")
  @ToString.Exclude
  private GitRepository repository;

  @Column(nullable = false, name = "test_name")
  private String testName;

  @Column(nullable = false, name = "class_name")
  private String className;

  @Column(nullable = false, name = "test_suite_name")
  private String testSuiteName;

  @Column(nullable = false, name = "flakiness_score")
  private double flakinessScore;

  @Column(nullable = false, name = "default_branch_failure_rate")
  private double defaultBranchFailureRate;

  @Column(nullable = false, name = "combined_failure_rate")
  private double combinedFailureRate;

  @Column(nullable = false, name = "last_updated")
  private OffsetDateTime lastUpdated;
}
