package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsPageRequest;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
  private static final double DEFAULT_BRANCH_WEIGHT = 0.7;
  private static final double COMBINED_BRANCH_WEIGHT = 0.3;
  private static final double MIN_FLAKY_RATE = 0.01; // 1%
  private static final double MAX_FLAKY_RATE = 0.5; // 50%

  private final TestCaseStatisticsRepository statisticsRepository;
  private final TestCaseFlakinessRepository flakinessRepository;

  /**
   * Composite key uniquely identifying one test case within a suite.
   * Package-private so {@link TestResultService} can build index keys without an extra lookup.
   */
  record StatsKey(String testName, String className, String testSuiteName) {}

  /**
   * Converts a flat list of {@link TestCaseStatistics} into a {@code Map} keyed by
   * {@link StatsKey} for O(1) per-test lookups.
   *
   * @param stats the statistics rows to index
   * @return map of composite key to failure rate
   */
  public static Map<StatsKey, Double> indexFailureRates(List<TestCaseStatistics> stats) {
    return stats.stream()
        .collect(
            Collectors.toMap(
                s -> new StatsKey(s.getTestName(), s.getClassName(), s.getTestSuiteName()),
                TestCaseStatistics::getFailureRate,
                (a, b) -> a));
  }

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
   *
   * <p>All statistics and existing flakiness records are fetched in two bulk queries and indexed
   * into {@link Map}s before the test-case loop, making per-test lookups O(1). Scores are persisted
   * even when equal to zero so consumers can distinguish "known non-flaky" from "no record yet".
   *
   * @param testSuites the suites processed in this run
   * @param defaultBranch the repository's default branch name
   * @param repository the repository
   */
  @Transactional
  public void updateFlakinessForTestSuite(
      List<TestSuite> testSuites, String defaultBranch, GitRepository repository) {

    var suiteNames = testSuites.stream().map(TestSuite::getName).distinct().toList();

    // Two bulk DB reads and index into Maps for O(1) per-test lookup
    Map<StatsKey, Double> defaultRates =
        indexFailureRates(
            statisticsRepository.findByTestSuiteNameInAndBranchNameAndRepositoryRepositoryId(
                suiteNames, defaultBranch, repository.getRepositoryId()));

    Map<StatsKey, Double> combinedRates =
        indexFailureRates(
            statisticsRepository.findByTestSuiteNameInAndBranchNameAndRepositoryRepositoryId(
                suiteNames, "combined", repository.getRepositoryId()));

    // One bulk read for existing flakiness records to avoid N+1 SELECTs in the loop
    Map<StatsKey, TestCaseFlakiness> existingFlakinessRecords = loadFlakinessIndex(
        suiteNames, repository.getRepositoryId());

    Map<StatsKey, TestCaseFlakiness> flakinessMapToSave = new LinkedHashMap<>();
    for (TestSuite suite : testSuites) {
      for (TestCase testCase : suite.getTestCases()) {
        var key = new StatsKey(testCase.getName(), testCase.getClassName(), suite.getName());
        var info = computeFlakinessInfo(
            testCase.getName(), testCase.getClassName(), suite.getName(),
            defaultRates, combinedRates);

        TestCaseFlakiness record = existingFlakinessRecords.getOrDefault(key, null);
        if (record == null) {
          record = new TestCaseFlakiness();
          record.setTestName(key.testName());
          record.setClassName(key.className());
          record.setTestSuiteName(key.testSuiteName());
          record.setRepository(repository);
        }
        record.setFlakinessScore(info.flakinessScore());
        record.setDefaultBranchFailureRate(info.defaultBranchFailureRate());
        record.setCombinedFailureRate(info.combinedFailureRate());
        record.setLastUpdated(OffsetDateTime.now());
        flakinessMapToSave.put(key, record);
      }
    }

    flakinessRepository.saveAll(flakinessMapToSave.values());
  }

  /**
   * Batch-fetches all {@link TestCaseFlakiness} rows for the given suite names and indexes them
   * by {@link StatsKey} for O(1) lookup. Exposed so {@link TestResultService} can pre-load
   * flakiness data before annotating per-run test cases.
   *
   * @param suiteNames suite names to scope the query
   * @param repositoryId the repository ID
   * @return map of composite key to the matching flakiness record
   */
  public Map<StatsKey, TestCaseFlakiness> loadFlakinessIndex(
      List<String> suiteNames, Long repositoryId) {
    return flakinessRepository
        .findByTestSuiteNameInAndRepositoryRepositoryId(suiteNames, repositoryId)
        .stream()
        .collect(
            Collectors.toMap(
                f -> new StatsKey(f.getTestName(), f.getClassName(), f.getTestSuiteName()),
                f -> f,
                (a, b) -> a));
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
                  flakinessRepository.findByRepositoryIdAndTestNameAndClassNameAndSuiteName(
                      repositoryId,
                      identifier.testName(),
                      identifier.className(),
                      identifier.testSuiteName());

              if (!matches.isEmpty()) {
                TestCaseFlakiness best = matches.getFirst();
                return new TestFlakinessScoreDto(
                    identifier.testName(),
                    identifier.className(),
                    identifier.testSuiteName(),
                    best.getFlakinessScore(),
                    best.getDefaultBranchFailureRate(),
                    best.getCombinedFailureRate());
              }
              return new TestFlakinessScoreDto(
                  identifier.testName(), identifier.className(), identifier.testSuiteName(),
                  0.0, 0.0, 0.0);
            })
        .toList();
  }

  /**
   * Builds a project-wide overview of all flaky tests for a repository.
   * Reads from the precomputed {@code test_case_flakiness} table - no full stats scan.
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
   * Computes flakiness information for a single test case using pre-indexed failure-rate maps.
   * Both maps should be built via {@link #indexFailureRates} before entering any per-test loop
   * so that each call here is O(1).
   *
   * @param testName the test name to look up
   * @param className the class name to look up
   * @param suiteName the test suite name to look up
   * @param defaultRates failure-rate index for the default branch
   * @param combinedRates failure-rate index for the combined pseudo-branch
   * @return computed flakiness info
   */
  public FlakinessInfo computeFlakinessInfo(
      String testName,
      String className,
      String suiteName,
      Map<StatsKey, Double> defaultRates,
      Map<StatsKey, Double> combinedRates) {
    var key = new StatsKey(testName, className, suiteName);
    double defaultFailureRate = defaultRates.getOrDefault(key, 0.0);
    double combinedFailureRate = combinedRates.getOrDefault(key, 0.0);
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
