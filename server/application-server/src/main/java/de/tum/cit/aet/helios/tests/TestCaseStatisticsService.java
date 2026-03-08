package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
  private static final double DEFAULT_BRANCH_WEIGHT = 0.7;
  private static final double COMBINED_BRANCH_WEIGHT = 0.3;
  private static final double MIN_FLAKY_RATE = 0.01; // 1%
  private static final double MAX_FLAKY_RATE = 0.5; // 50%

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
   * Returns flakiness scores for the requested test cases identified by testName + className.
   * Matches against accumulated default-branch and combined-branch statistics.
   *
   * @param repositoryId the repository ID
   * @param testIdentifiers the list of test case identifiers to look up
   * @return matching flakiness scores
   */
  public List<TestFlakinessScoreDto> getFlakinessScoresForTests(
      Long repositoryId,
      List<TestFlakinessScoreRequest.TestCaseIdentifier> testIdentifiers) {
    // Step 1: Fetch historical statistics using the getStatisticsForBranch method.
    // We need two sets: default-branch stats and combined-branch stats
    var defaultBranch =
        branchRepository
            .findFirstByRepositoryRepositoryIdAndIsDefaultTrue(repositoryId)
            .orElseThrow();
    String defaultBranchName = defaultBranch.getName();
    List<TestCaseStatistics> defaultBranchStats =
        getStatisticsForBranch(defaultBranchName, repositoryId);
    List<TestCaseStatistics> combinedStats =
        getStatisticsForBranch("combined", repositoryId);

    // Step 2: For each requested test identifier,
    // compute the flakiness score using the pre-fetched stats
    List<TestFlakinessScoreDto> flakinessScoreDtos = new ArrayList<>();
    testIdentifiers.forEach(testIdentifier -> {
      FlakinessInfo flakinessInfo = computeFlakinessInfo(
          testIdentifier.testName(),
          testIdentifier.className(),
          defaultBranchStats,
          combinedStats
      );
      TestFlakinessScoreDto testFlakinessScoreDto = new TestFlakinessScoreDto(
          testIdentifier.testName(),
          testIdentifier.className(),
          flakinessInfo.flakinessScore(),
          flakinessInfo.defaultBranchFailureRate(),
          flakinessInfo.combinedFailureRate());
      flakinessScoreDtos.add(testFlakinessScoreDto);
    });
    return flakinessScoreDtos;
  }

  /** Holds computed flakiness information for a single test case. */
  public record FlakinessInfo(
      double flakinessScore,
      double defaultBranchFailureRate,
      double combinedFailureRate
  ) {}

  /**
   * Computes flakiness information for a single test case from pre-fetched statistics lists.
   * This is the shared core logic.
   *
   * Each caller pre-fetches the two stat lists (default branch + combined),
   * then delegates the per-test-case score computation here.
   *
   * @param testName the test name to look up
   * @param className the class name to look up
   * @param defaultBranchStats statistics for the default branch
   * @param combinedStats statistics for the "combined" pseudo-branch
   * @return computed flakiness info
   */
  public FlakinessInfo computeFlakinessInfo(
      String testName,
      String className,
      List<TestCaseStatistics> defaultBranchStats,
      List<TestCaseStatistics> combinedStats) {
    double defaultFailureRate =
        defaultBranchStats.stream()
            .filter(
                s ->
                    s.getTestName().equals(testName) && s.getClassName().equals(className))
            .findFirst()
            .map(TestCaseStatistics::getFailureRate)
            .orElse(0.0);

    double combinedFailureRate =
        combinedStats.stream()
            .filter(
                s ->
                    s.getTestName().equals(testName)
                        && s.getClassName().equals(className))
            .findFirst()
            .map(TestCaseStatistics::getFailureRate)
            .orElse(0.0);

    double flakinessScore = calculateFlakinessScore(defaultFailureRate, combinedFailureRate);
    return new FlakinessInfo(
        flakinessScore,
        defaultFailureRate,
        combinedFailureRate);
  }

  /**
   * Calculates a flakiness score based on weighted default and combined branch failure rates. The
   * score ranges from 0 to 100 (not flaky to highly flaky).
   *
   * @param defaultBranchFailureRate Failure rate of the test on the default branch
   * @param combinedBranchFailureRate Failure rate of the test across all branches combined
   * @return A flakiness score between 0 and 100
   */
  public double calculateFlakinessScore(
      double defaultBranchFailureRate, double combinedBranchFailureRate) {

    // Default branch flakiness score
    double defaultFlakiness = 0.0;
    if (defaultBranchFailureRate > MIN_FLAKY_RATE && defaultBranchFailureRate < MAX_FLAKY_RATE) {
      // Higher score for failure rates closer to MIN_FLAKY_RATE (slightly flaky)
      // Lower score for failure rates closer to MAX_FLAKY_RATE (more broken than flaky)
      defaultFlakiness = (MAX_FLAKY_RATE - defaultBranchFailureRate) / MAX_FLAKY_RATE;
    }

    // Combined branches flakiness score
    double combinedFlakiness = 0.0;
    if (combinedBranchFailureRate > MIN_FLAKY_RATE && combinedBranchFailureRate < MAX_FLAKY_RATE) {
      combinedFlakiness = (MAX_FLAKY_RATE - combinedBranchFailureRate) / MAX_FLAKY_RATE;
    }
    // Calculate weighted score and convert to a 0-100 scale
    double weightedScore =
        ((defaultFlakiness * DEFAULT_BRANCH_WEIGHT) + (combinedFlakiness * COMBINED_BRANCH_WEIGHT))
            * 100;

    return weightedScore;
  }
}
