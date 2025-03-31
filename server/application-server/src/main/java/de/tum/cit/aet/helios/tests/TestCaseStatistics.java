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
 * Entity representing statistics for a test case across multiple runs. Used for flaky test
 * detection.
 */
@Entity
@Table(
    name = "test_case_statistics",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_test_case_statistics",
          columnNames = {
            "test_name",
            "class_name",
            "test_suite_name",
            "branch_name",
            "repository_id"
          })
    },
    indexes = {
      @Index(
          name = "idx_test_case_statistics",
          columnList = "test_name,class_name,test_suite_name,branch_name,repository_id"),
      @Index(name = "idx_branch_name", columnList = "branch_name,repository_id"),
      @Index(name = "idx_is_flaky", columnList = "branch_name,is_flaky,repository_id")
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

  @Column(nullable = false, name = "branch_name")
  private String branchName;

  @Column(nullable = false)
  private int totalRuns;

  @Column(nullable = false, name = "failed_runs")
  private int failedRuns;

  @Column(nullable = false, name = "failure_rate")
  private double failureRate;

  @Column(nullable = false, name = "is_flaky")
  private boolean isFlaky;

  @Column(nullable = false, name = "last_updated")
  private OffsetDateTime lastUpdated;

  /**
   * Sets the test name, truncating if necessary to fit the database column.
   *
   * @param testName the test name to set
   */
  public void setTestName(String testName) {
    this.testName = testName.length() > 255 ? testName.substring(0, 255) : testName;
  }

  /**
   * Sets the class name, truncating if necessary to fit the database column.
   *
   * @param className the class name to set
   */
  public void setClassName(String className) {
    this.className = className.length() > 255 ? className.substring(0, 255) : className;
  }

  /**
   * Sets the test suite name, truncating if necessary to fit the database column.
   *
   * @param testSuiteName the test suite name to set
   */
  public void setTestSuiteName(String testSuiteName) {
    this.testSuiteName =
        testSuiteName.length() > 255 ? testSuiteName.substring(0, 255) : testSuiteName;
  }

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
    this.calculateFailureRate();
    this.lastUpdated = OffsetDateTime.now();
  }

  /**
   * Calculates the failure rate and updates the flakiness status based on the configured threshold.
   */
  private void calculateFailureRate() {
    this.failureRate = totalRuns > 0 ? (double) failedRuns / totalRuns : 0.0;
    // A test is considered flaky if it fails more than 50% of the time
    this.isFlaky = this.failureRate >= 0.5;
  }
}
