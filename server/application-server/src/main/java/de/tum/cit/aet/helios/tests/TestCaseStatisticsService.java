package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsPageRequest;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private static final int SUITE_NAME_QUERY_CHUNK_SIZE = 128;

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
   * Recomputes and persists flakiness scores for all test cases in the given suites.
   *
   * <p>Failure rates are loaded and indexed per suite chunk (default branch + combined) so only a
   * bounded working set is kept in memory. Each score is written via a native
   * {@code INSERT ... ON CONFLICT DO UPDATE} that bypasses the Hibernate first-level cache
   * entirely, preventing session growth and handling concurrent calls safely.
   *
   * @param testSuites the suites processed in this run
   * @param defaultBranch the repository's default branch name
   * @param repository the repository
   */
  @Transactional
  public void updateFlakinessForTestSuite(
      List<TestSuite> testSuites, String defaultBranch, GitRepository repository) {

    OffsetDateTime now = OffsetDateTime.now();
    int count = 0;
    for (List<TestSuite> suiteChunk : chunked(testSuites, SUITE_NAME_QUERY_CHUNK_SIZE)) {
      Set<String> suiteNamesInChunk =
          suiteChunk.stream().map(TestSuite::getName).collect(Collectors.toSet());
      Set<StatsKey> chunkKeys = collectChunkKeys(suiteChunk);
      Map<StatsKey, Double> defaultRates =
          loadFailureRateIndex(
              suiteNamesInChunk,
              defaultBranch,
              repository.getRepositoryId(),
              chunkKeys);
      Map<StatsKey, Double> combinedRates =
          loadFailureRateIndex(
              suiteNamesInChunk,
              "combined",
              repository.getRepositoryId(),
              chunkKeys);

      for (TestSuite suite : suiteChunk) {
        for (TestCase testCase : suite.getTestCases()) {
          var info = computeFlakinessInfo(
              testCase.getName(),
              testCase.getClassName(),
              suite.getName(),
              defaultRates,
              combinedRates);
          flakinessRepository.upsertFlakiness(
              repository.getRepositoryId(),
              testCase.getName(),
              testCase.getClassName(),
              suite.getName(),
              info.flakinessScore(),
              info.defaultBranchFailureRate(),
              info.combinedFailureRate(),
              now);
          count++;
        }
      }
    }
    log.info("Finished flakiness upsert for repository {}: rows={}",
        repository.getRepositoryId(), count);
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

  private Map<StatsKey, Double> loadFailureRateIndex(
      Collection<String> suiteNames,
      String branchName,
      Long repositoryId,
      Set<StatsKey> expectedKeys) {
    if (suiteNames.isEmpty() || expectedKeys.isEmpty()) {
      return Map.of();
    }

    Map<StatsKey, Double> rates = new HashMap<>();
    List<TestCaseStatisticsRepository.FailureRateRow> rows =
        statisticsRepository
            .findFailureRateRowsByTestSuiteNameInAndBranchNameAndRepositoryRepositoryId(
                suiteNames,
                branchName,
                repositoryId);
    for (TestCaseStatisticsRepository.FailureRateRow row : rows) {
      StatsKey key = new StatsKey(row.getTestName(), row.getClassName(), row.getTestSuiteName());
      if (!expectedKeys.contains(key)) {
        continue;
      }
      rates.put(
          key,
          row.getTotalRuns() > 0 ? (double) row.getFailedRuns() / row.getTotalRuns() : 0.0);
    }
    log.info(
        "Loaded failure rates for repository {} branch {} chunkSuites={} matchedRows={}",
        repositoryId,
        branchName,
        suiteNames.size(),
        rates.size());
    return rates;
  }

  private static Set<StatsKey> collectChunkKeys(List<TestSuite> suiteChunk) {
    return suiteChunk.stream()
        .flatMap(
            suite ->
                suite.getTestCases().stream()
                    .map(testCase -> new StatsKey(
                        testCase.getName(), testCase.getClassName(), suite.getName())))
        .collect(Collectors.toSet());
  }

  private static <T> List<List<T>> chunked(List<T> items, int chunkSize) {
    if (items.isEmpty()) {
      return List.of();
    }
    List<List<T>> chunks = new ArrayList<>((items.size() + chunkSize - 1) / chunkSize);
    for (int i = 0; i < items.size(); i += chunkSize) {
      chunks.add(items.subList(i, Math.min(items.size(), i + chunkSize)));
    }
    return chunks;
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
    int totalTrackedTests = (int) flakinessRepository.countByRepositoryRepositoryId(repositoryId);
    int totalFlakyTests = (int) flakinessRepository
        .countByRepositoryRepositoryIdAndFlakinessScoreGreaterThan(repositoryId, 0);
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

      // Retrieve only tests with a flakiness score greater than zero
      predicates.add(cb.greaterThan(root.get("flakinessScore"), 0));

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
