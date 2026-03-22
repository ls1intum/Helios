package de.tum.cit.aet.helios.tests;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * Entity representing statistics for a test case across multiple runs. Used for flaky test
 * detection. Keyed by (test_case_id, branch_name) instead of string-based identity.
 */
@Entity
@Table(
    name = "test_case_statistics",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_test_case_statistics",
          columnNames = {"test_case_id", "branch_name"})
    },
    indexes = {
        @Index(name = "idx_test_case_statistics_case", columnList = "test_case_id"),
        @Index(name = "idx_test_case_statistics_branch", columnList = "branch_name")
    })
@Getter
@Setter
@NoArgsConstructor
@ToString
@Filter(name = "gitRepositoryFilter")
public class TestCaseStatistics {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "test_case_id", nullable = false)
  @ToString.Exclude
  private TestCase testCase;

  @Column(nullable = false, name = "branch_name")
  private String branchName;

  @Column(nullable = false)
  private int totalRuns;

  @Column(nullable = false, name = "failed_runs")
  private int failedRuns;

  @Column(nullable = false, name = "last_updated")
  private OffsetDateTime lastUpdated;

  /**
   * Sets the branch name, truncating if necessary to fit the database column.
   *
   * @param branchName the branch name to set
   */
  public void setBranchName(String branchName) {
    this.branchName = branchName.length() > 255 ? branchName.substring(0, 255) : branchName;
  }

  /**
   * Updates the statistics with a new test run result.
   *
   * @param hasFailed whether the test failed in this run
   */
  public void addRun(boolean hasFailed) {
    this.totalRuns++;
    if (hasFailed) {
      this.failedRuns++;
    }
    this.lastUpdated = OffsetDateTime.now();
  }

  public double getFailureRate() {
    return totalRuns > 0 ? (double) failedRuns / totalRuns : 0.0;
  }
}
