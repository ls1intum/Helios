package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/** Service for managing test case statistics and detecting flaky tests. */
@Service
@Log4j2
@RequiredArgsConstructor
public class TestCaseStatisticsService {

  private final TestCaseStatisticsRepository statisticsRepository;
  private final BranchRepository branchRepository;

  /**
   * Updates statistics for a test case, creating a new entry if it doesn't exist.
   *
   * @param testName the name of the test
   * @param className the class name of the test
   * @param testSuiteName the test suite name
   * @param branchName the branch name
   * @param hasFailed whether the test failed in this run
   * @param repository the repository
   * @return the updated statistics
   */
  @Transactional
  public TestCaseStatistics updateStatistics(
      String testName,
      String className,
      String testSuiteName,
      String branchName,
      boolean hasFailed,
      GitRepository repository) {
    Optional<TestCaseStatistics> existingStats =
        statisticsRepository
            .findByTestNameAndClassNameAndTestSuiteNameAndBranchNameAndRepositoryRepositoryId(
                testName, className, testSuiteName, branchName, repository.getRepositoryId());

    TestCaseStatistics statistics;
    if (existingStats.isPresent()) {
      statistics = existingStats.get();
    } else {
      statistics = new TestCaseStatistics();
      statistics.setTestName(testName);
      statistics.setClassName(className);
      statistics.setTestSuiteName(testSuiteName);
      statistics.setBranchName(branchName);
      statistics.setTotalRuns(0);
      statistics.setFailedRuns(0);
      statistics.setFailureRate(0.0);
      statistics.setFlaky(false);
      statistics.setLastUpdated(OffsetDateTime.now());
      statistics.setRepository(repository);
    }

    statistics.addRun(hasFailed);
    return statisticsRepository.save(statistics);
  }

  /**
   * Updates statistics for multiple test cases from a test suite.
   *
   * @param testSuite the test suite containing test cases
   * @param branchName the branch name
   * @param repository the repository
   */
  @Transactional
  public void updateStatisticsForTestSuite(
      TestSuite testSuite, String branchName, GitRepository repository) {
    String testSuiteName = testSuite.getName();

    for (TestCase testCase : testSuite.getTestCases()) {
      boolean hasFailed =
          testCase.getStatus() == TestCase.TestStatus.FAILED
              || testCase.getStatus() == TestCase.TestStatus.ERROR;

      updateStatistics(
          testCase.getName(),
          testCase.getClassName(),
          testSuiteName,
          branchName,
          hasFailed,
          repository);
    }
  }

  /**
   * Gets statistics for a specific test case on a specific branch.
   *
   * @param testName the name of the test
   * @param className the class name of the test
   * @param testSuiteName the test suite name
   * @param branchName the branch name
   * @return the statistics if found
   */
  public Optional<TestCaseStatistics> getStatistics(
      String testName, String className, String testSuiteName, String branchName) {
    return statisticsRepository.findByTestNameAndClassNameAndTestSuiteNameAndBranchName(
        testName, className, testSuiteName, branchName);
  }

  /**
   * Gets all statistics for a specific branch.
   *
   * @param branchName the branch name
   * @param repositoryId the repository ID
   * @return list of statistics for all tests on the branch
   */
  public List<TestCaseStatistics> getStatisticsForBranch(String branchName, Long repositoryId) {
    return statisticsRepository.findByBranchNameAndRepositoryRepositoryId(branchName, repositoryId);
  }

  /**
   * Gets flaky or non-flaky tests for a specific branch.
   *
   * @param branchName the branch name
   * @param isFlaky whether to find flaky or non-flaky tests
   * @return list of flaky or non-flaky test statistics
   */
  public List<TestCaseStatistics> getTestsByFlakinessForBranch(String branchName, boolean isFlaky) {
    return statisticsRepository.findByBranchNameAndIsFlaky(branchName, isFlaky);
  }

  /**
   * Gets flaky or non-flaky tests for the default branch of a repository.
   *
   * @param repository the repository
   * @param isFlaky whether to find flaky or non-flaky tests
   * @return list of flaky or non-flaky test statistics for the default branch
   */
  public List<TestCaseStatistics> getTestsByFlakinessForDefaultBranch(
      GitRepository repository, boolean isFlaky) {
    Optional<Branch> defaultBranch =
        branchRepository.findAll().stream()
            .filter(branch -> branch.getRepository().equals(repository) && branch.isDefault())
            .findFirst();

    if (defaultBranch.isPresent()) {
      return getTestsByFlakinessForBranch(defaultBranch.get().getName(), isFlaky);
    } else {
      log.warn("No default branch found for repository {}", repository.getNameWithOwner());
      return List.of();
    }
  }

  /**
   * Gets all statistics for the default branch of a repository.
   *
   * @param repository the repository
   * @return list of statistics for all tests on the default branch
   */
  public List<TestCaseStatistics> getStatisticsForDefaultBranch(GitRepository repository) {
    Optional<Branch> defaultBranch =
        branchRepository.findAll().stream()
            .filter(branch -> branch.getRepository().equals(repository) && branch.isDefault())
            .findFirst();

    if (defaultBranch.isPresent()) {
      return getStatisticsForBranch(defaultBranch.get().getName(), repository.getRepositoryId());
    } else {
      log.warn("No default branch found for repository {}", repository.getNameWithOwner());
      return List.of();
    }
  }
}
