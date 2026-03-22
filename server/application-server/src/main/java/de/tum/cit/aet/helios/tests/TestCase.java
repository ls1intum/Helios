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
import org.hibernate.annotations.Filter;

/**
 * Canonical test case definition. One row per unique (repository, suite_name, class_name, name)
 * combination. Persists across workflow runs and holds precomputed flakiness metrics.
 */
@Entity
@Table(
    name = "test_case",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_test_case_identity",
            columnNames = {"repository_id", "suite_name", "class_name", "name"})
    },
    indexes = {
        @Index(name = "idx_test_case_repo_flakiness", columnList = "repository_id,flakiness_score DESC")
    })
@Getter
@Setter
@NoArgsConstructor
@ToString
@Filter(name = "gitRepositoryFilter")
public class TestCase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "repository_id")
  @ToString.Exclude
  private GitRepository repository;

  @Column(nullable = false, name = "suite_name")
  private String suiteName;

  @Column(nullable = false, name = "class_name")
  private String className;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, name = "flakiness_score")
  private double flakinessScore;

  @Column(nullable = false, name = "default_branch_failure_rate")
  private double defaultBranchFailureRate;

  @Column(nullable = false, name = "combined_failure_rate")
  private double combinedFailureRate;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  public void setName(String name) {
    this.name = name.length() > 255 ? name.substring(0, 255) : name;
  }

  public void setClassName(String className) {
    this.className = className.length() > 255 ? className.substring(0, 255) : className;
  }
}
