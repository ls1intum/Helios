package de.tum.cit.aet.helios.tests;

import java.util.Collection;
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
   * Find all statistics for test cases that belong to specific test suites on a branch in a
   * repository.
   *
   * @param testSuiteNames collection of test suite names to search for
   * @param branchName the branch name
   * @param repositoryId the repository ID
   * @return list of statistics for matching test cases
   */
  List<TestCaseStatistics> findByTestSuiteNameInAndBranchNameAndRepositoryRepositoryId(
      Collection<String> testSuiteNames, String branchName, Long repositoryId);
}
