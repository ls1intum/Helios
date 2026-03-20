package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsPageRequest;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** Service for managing test case statistics and detecting flaky tests. */
@Service
@Log4j2
@RequiredArgsConstructor
public class TestCaseStatisticsService {

  public static final double HIGH_FLAKINESS_THRESHOLD = 70.0;
  public static final double LOW_FLAKINESS_THRESHOLD = 30.0;
  private final TestCaseStatisticsRepository statisticsRepository;
  private final TestCaseFlakinessRepository flakinessRepository;

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
   * Recomputes and persists flakiness scores for all tests belonging to the given suites.
   * Reads statistics for the default branch and combined branches to compute scores, then updates
   * the {@code test_case_flakiness} table accordingly. Scores are persisted even when equal to
   * zero so consumers can distinguish "known non-flaky" from "no record yet".
   *
   * @param testSuites the suites processed in this run
   * @param defaultBranch the repository's default branch name
   * @param repository the repository
   */
  public void updateFlakinessForTestSuite(
      List<TestSuite> testSuites, String defaultBranch, GitRepository repository) {

    var suiteNames = testSuites.stream().map(TestSuite::getName).distinct().toList();

    List<TestCaseStatistics> defaultBranchStats =
        statisticsRepository.findByTestSuiteNameInAndBranchNameAndRepositoryRepositoryId(
            suiteNames, defaultBranch, repository.getRepositoryId());

    List<TestCaseStatistics> combinedStats =
        statisticsRepository.findByTestSuiteNameInAndBranchNameAndRepositoryRepositoryId(
            suiteNames, "combined", repository.getRepositoryId());

    for (TestSuite suite : testSuites) {
      for (TestCase testCase : suite.getTestCases()) {
        var flakinessInfo =
            computeFlakinessInfo(
                testCase.getName(), testCase.getClassName(), suite.getName(),
                defaultBranchStats, combinedStats);
        double flakinessScore = flakinessInfo.flakinessScore();

        upsertFlakiness(
            testCase.getName(),
            testCase.getClassName(),
            suite.getName(),
            flakinessScore,
            flakinessInfo.defaultBranchFailureRate(),
            flakinessInfo.combinedFailureRate(),
            repository);
      }
    }
  }

  /**
   * Inserts or updates a flakiness record for a single test.
   */
  private void upsertFlakiness(
      String testName,
      String className,
      String testSuiteName,
      double score,
      double defaultRate,
      double combinedRate,
      GitRepository repository) {
    TestCaseFlakiness flakiness =
        flakinessRepository
            .findByTestNameAndClassNameAndTestSuiteNameAndRepositoryRepositoryId(
                testName, className, testSuiteName, repository.getRepositoryId())
            .orElseGet(
                () -> {
                  TestCaseFlakiness f = new TestCaseFlakiness();
                  f.setTestName(testName);
                  f.setClassName(className);
                  f.setTestSuiteName(testSuiteName);
                  f.setRepository(repository);
                  return f;
                });

    flakiness.setFlakinessScore(score);
    flakiness.setDefaultBranchFailureRate(defaultRate);
    flakiness.setCombinedFailureRate(combinedRate);
    flakiness.setLastUpdated(OffsetDateTime.now());
    flakinessRepository.save(flakiness);
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
   * Returns flakiness scores for the requested test cases identified by testName + className.
   * Reads directly from the precomputed {@code test_case_flakiness} table.
   *
   * @param repositoryId the repository ID
   * @param testIdentifiers the list of test case identifiers to look up
   * @return matching flakiness scores
   */
  public List<TestFlakinessScoreDto> getFlakinessScoresForTests(
      Long repositoryId, List<TestFlakinessScoreRequest.TestCaseIdentifier> testIdentifiers) {
    return testIdentifiers.stream()
        .map(
            identifier -> {
              List<TestCaseFlakiness> matches =
                  flakinessRepository.findByRepositoryIdAndTestNameAndClassName(
                      repositoryId, identifier.testName(), identifier.className());

              if (!matches.isEmpty()) {
                TestCaseFlakiness best = matches.getFirst();
                return new TestFlakinessScoreDto(
                    identifier.testName(),
                    identifier.className(),
                    best.getFlakinessScore(),
                    best.getDefaultBranchFailureRate(),
                    best.getCombinedFailureRate());
              }
              return new TestFlakinessScoreDto(
                  identifier.testName(), identifier.className(), 0.0, 0.0, 0.0);
            })
        .toList();
  }

  /**
   * Builds a project-wide overview of all flaky tests for a repository.
   * Reads from the precomputed {@code test_case_flakiness} table — no full stats scan.
   * {@code totalRuns} is resolved in a single batch query against {@code test_case_statistics}
   * (combined branch) scoped to only the suites that have flaky tests.
   *
   * @param repositoryId the repository ID
   * @param request the pagination and filtering parameters
   * @return aggregated flaky test overview
   */
  public FlakyTestOverviewDto getFlakyTestsOverview(
      Long repositoryId, FlakyTestsPageRequest request) {
    Sort sort = resolveSort(request);
    // Frontend sends 1-based page numbers; convert to 0-based for Spring Data.
    int zeroBasedPage = Math.max(0, request.getPage() - 1);
    Pageable pageable = PageRequest.of(zeroBasedPage, request.getSize(), sort);

    Specification<TestCaseFlakiness> spec = buildTestCaseFlakinessSpecification(
        request, repositoryId);

    Page<TestCaseFlakiness> resultPage = flakinessRepository.findAll(spec, pageable);
    List<TestCaseFlakiness> flakyTests = resultPage.getContent();
    List<FlakyTestOverviewDto.FlakyTestDto> flakyTestDtos =
        flakyTests.stream().map(FlakyTestOverviewDto.FlakyTestDto::from).toList();

    FlakyTestOverviewDto.FlakyTestSummary summary = buildGlobalSummary(repositoryId);

    return new FlakyTestOverviewDto(summary, flakyTestDtos, resultPage.getTotalElements());
  }

  private FlakyTestOverviewDto.FlakyTestSummary buildGlobalSummary(Long repositoryId) {
    int totalTrackedTests = (int) statisticsRepository
        .countByBranchNameAndRepositoryRepositoryId("combined", repositoryId);
    int totalFlakyTests = (int) flakinessRepository.countByRepositoryRepositoryId(repositoryId);
    int highFlakinessCount = (int) flakinessRepository
        .countByRepositoryRepositoryIdAndFlakinessScoreGreaterThan(
            repositoryId, HIGH_FLAKINESS_THRESHOLD);
    int mediumFlakinessCount = (int) flakinessRepository
        .countByRepositoryRepositoryIdAndFlakinessScoreGreaterThanAndFlakinessScoreLessThanEqual(
            repositoryId, LOW_FLAKINESS_THRESHOLD, HIGH_FLAKINESS_THRESHOLD);
    int lowFlakinessCount = totalFlakyTests - highFlakinessCount - mediumFlakinessCount;

    return new FlakyTestOverviewDto.FlakyTestSummary(
        totalTrackedTests,
        totalFlakyTests,
        highFlakinessCount,
        mediumFlakinessCount,
        lowFlakinessCount);
  }

  /** Holds computed flakiness information for a single test case. */
  public record FlakinessInfo(
      double flakinessScore, double defaultBranchFailureRate, double combinedFailureRate) {}

  /**
   * Computes flakiness information for a single test case from pre-fetched statistics lists.
   * Used by {@link TestResultService} to annotate per-run test results.
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
      String suiteName,
      List<TestCaseStatistics> defaultBranchStats,
      List<TestCaseStatistics> combinedStats) {
    double defaultFailureRate =
        defaultBranchStats.stream()
            .filter(s ->
                s.getTestName().equals(testName)
                && s.getClassName().equals(className)
                && s.getTestSuiteName().equals(suiteName))
            .findFirst()
            .map(TestCaseStatistics::getFailureRate)
            .orElse(0.0);

    double combinedFailureRate =
        combinedStats.stream()
            .filter(s ->
                s.getTestName().equals(testName)
                && s.getClassName().equals(className)
                && s.getTestSuiteName().equals(suiteName))
            .findFirst()
            .map(TestCaseStatistics::getFailureRate)
            .orElse(0.0);

    double flakinessScore = calculateFlakinessScore(defaultFailureRate, combinedFailureRate);
    return new FlakinessInfo(flakinessScore, defaultFailureRate, combinedFailureRate);
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

  private Sort resolveSort(FlakyTestsPageRequest request) {
    String sortDirection = request.getSortDirection();
    String sortField = "flakinessScore"; // Default sort field

    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, sortField);
  }

  private Specification<TestCaseFlakiness> buildTestCaseFlakinessSpecification(
      FlakyTestsPageRequest request, Long repositoryId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always scope to current repository
      predicates.add(cb.equal(root.get("repository").get("repositoryId"), repositoryId));

      // Search term across testName, className, and testSuiteName
      if (request.getSearchTerm() != null && !request.getSearchTerm().trim().isEmpty()) {
        String term = "%" + request.getSearchTerm().trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("testName")), term),
                cb.like(cb.lower(root.get("className")), term),
                cb.like(cb.lower(root.get("testSuiteName")), term)));
      }

      // Filter by flakiness score based on filter type
      switch (request.getFilterType()) {
        case HIGH:
          predicates.add(cb.greaterThan(root.get("flakinessScore"), HIGH_FLAKINESS_THRESHOLD));
          break;
        case MEDIUM:
          predicates.add(cb.and(
              cb.greaterThan(root.get("flakinessScore"), LOW_FLAKINESS_THRESHOLD),
              cb.lessThanOrEqualTo(root.get("flakinessScore"), HIGH_FLAKINESS_THRESHOLD)
          ));
          break;
        case LOW:
          predicates.add(cb.lessThanOrEqualTo(root.get("flakinessScore"), LOW_FLAKINESS_THRESHOLD));
          break;
        case ALL:
        default:
          break;
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
