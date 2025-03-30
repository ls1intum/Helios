package de.tum.cit.aet.helios.tests;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for test case statistics. Provides methods to find and manage statistics for test
 * cases.
 */
@Repository
public interface TestCaseStatisticsRepository extends JpaRepository<TestCaseStatistics, Long> {

  /**
   * Find statistics for a specific test case on a specific branch.
   *
   * @param testName the name of the test
   * @param className the class name of the test
   * @param testSuiteName the test suite name
   * @param branchName the branch name
   * @param repositoryId the repository ID
   * @return the statistics if found
   */
  Optional<TestCaseStatistics>
      findByTestNameAndClassNameAndTestSuiteNameAndBranchNameAndRepositoryRepositoryId(
          String testName,
          String className,
          String testSuiteName,
          String branchName,
          Long repositoryId);

  /**
   * Find statistics for a specific test case on a specific branch.
   *
   * @param testName the name of the test
   * @param className the class name of the test
   * @param testSuiteName the test suite name
   * @param branchName the branch name
   * @return the statistics if found
   */
  Optional<TestCaseStatistics> findByTestNameAndClassNameAndTestSuiteNameAndBranchName(
      String testName, String className, String testSuiteName, String branchName);

  /**
   * Find all statistics for a specific branch.
   *
   * @param branchName the branch name
   * @param repositoryId the repository ID
   * @return list of statistics for all tests on the branch
   */
  List<TestCaseStatistics> findByBranchNameAndRepositoryRepositoryId(
      String branchName, Long repositoryId);

  /**
   * Find all flaky or non-flaky tests for a specific branch.
   *
   * @param branchName the branch name
   * @param isFlaky whether to find flaky or non-flaky tests
   * @return list of flaky or non-flaky test statistics
   */
  List<TestCaseStatistics> findByBranchNameAndIsFlaky(String branchName, boolean isFlaky);
}
