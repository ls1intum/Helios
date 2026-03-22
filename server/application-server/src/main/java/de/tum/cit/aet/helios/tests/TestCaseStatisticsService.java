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
  private final TestCaseRepository testCaseRepository;

  /**
   * Converts a flat list of {@link TestCaseStatistics} into a {@code Map} keyed by
   * test case ID for O(1) lookups.
   *
   * @param stats the statistics rows to index
   * @return map of composite key to failure rate
   */
  public static Map<Long, Double> indexFailureRates(List<TestCaseStatistics> stats) {
    return stats.stream()
        .collect(
            Collectors.toMap(
                s -> s.getTestCase().getId(),
                TestCaseStatistics::getFailureRate,
                (a, b) -> a));
  }

  /**
   * Updates statistics for a test case, creating a new entry if it doesn't exist.
   *
   * @param testCase the test case to update stats for
   * @param branchName the branch name
   * @param hasFailed whether the test failed in this run
   * @return the updated statistics
   */
  @Transactional
  public TestCaseStatistics updateStatistics(
      TestCase testCase, String branchName, boolean hasFailed) {
    Optional<TestCaseStatistics> existingStats =
        statisticsRepository.findByTestCaseIdAndBranchName(testCase.getId(), branchName);

    TestCaseStatistics statistics;
    if (existingStats.isPresent()) {
      statistics = existingStats.get();
    } else {
      statistics = new TestCaseStatistics();
      statistics.setTestCase(testCase);
      statistics.setBranchName(branchName);
      statistics.setTotalRuns(0);
      statistics.setFailedRuns(0);
      statistics.setLastUpdated(OffsetDateTime.now());
    }

    statistics.addRun(hasFailed);
    return statisticsRepository.save(statistics);
  }

  /**
   * Updates statistics for multiple test cases from a test suite.
   *
   * @param testSuiteRun the test suite containing test cases
   * @param branchName the branch name
   * @param repository the repository
   */
  @Transactional
  public void updateStatisticsForTestSuiteRun(TestSuiteRun testSuiteRun, String branchName) {

    for (TestCaseRun testCaseRun : testSuiteRun.getTestCaseRuns()) {
      boolean hasFailed =
          testCaseRun.getStatus() == TestCaseRun.TestStatus.FAILED
              || testCaseRun.getStatus() == TestCaseRun.TestStatus.ERROR;

      updateStatistics(
          testCaseRun.getTestCase(),
          branchName,
          hasFailed);
    }
  }

  /**
   * Recomputes and persists flakiness scores for all tests belonging to the given suites.
   *
   * <p>All statistics and existing flakiness records are fetched in two bulk queries and indexed
   * into {@link Map}s before the test-case loop, making per-test lookups O(1). Scores are persisted
   * even when equal to zero so consumers can distinguish "known non-flaky" from "no record yet".
   *
   * @param testSuiteRuns the suites processed in this run
   * @param defaultBranch the repository's default branch name
   * @param repository the repository
   */
  @Transactional
  public void updateFlakinessForTestSuiteRun(
      List<TestSuiteRun> testSuiteRuns, String defaultBranch, GitRepository repository) {

    List<Long> testCaseIds =
        testSuiteRuns.stream()
            .flatMap(tsr -> tsr.getTestCaseRuns().stream())
            .map(tcr -> tcr.getTestCase().getId())
            .distinct()
            .toList();

    var suiteNames = testSuiteRuns.stream().map(TestSuiteRun::getName).distinct().toList();

    // Two bulk DB reads and index into Maps for O(1) per-test lookup
    Map<Long, Double> defaultRates =
        indexFailureRates(
            statisticsRepository.findByTestCaseIdInAndBranchName(testCaseIds, defaultBranch));

    Map<Long, Double> combinedRates =
        indexFailureRates(
            statisticsRepository.findByTestCaseIdInAndBranchName(testCaseIds, "combined"));

    Map<Long, TestCase> testCaseMapToSave = new LinkedHashMap<>();
    for (TestSuiteRun suiteRun : testSuiteRuns) {
      for (TestCaseRun testCaseRun : suiteRun.getTestCaseRuns()) {
        TestCase testCaseDef = testCaseRun.getTestCase();
        Long testCaseId = testCaseDef.getId();

        double defaultFailureRate = defaultRates.getOrDefault(testCaseId, 0.0);
        double combinedFailureRate = combinedRates.getOrDefault(testCaseId, 0.0);
        double flakinessScore = calculateFlakinessScore(defaultFailureRate, combinedFailureRate);

        testCaseDef.setFlakinessScore(flakinessScore);
        testCaseDef.setDefaultBranchFailureRate(defaultFailureRate);
        testCaseDef.setCombinedFailureRate(combinedFailureRate);
        testCaseDef.setUpdatedAt(OffsetDateTime.now());
        testCaseMapToSave.put(testCaseId, testCaseDef);
      }
    }

    testCaseRepository.saveAll(testCaseMapToSave.values());
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
              Optional<TestCase> match =
                  testCaseRepository.findByRepositoryRepositoryIdAndSuiteNameAndClassNameAndName(
                      repositoryId,
                      identifier.testSuiteName(),
                      identifier.className(),
                      identifier.testName());
              return match.map(TestFlakinessScoreDto::from)
                  .orElseGet(() -> new TestFlakinessScoreDto(
                      identifier.testName(), identifier.className(), identifier.testSuiteName(),
                      0.0, 0.0, 0.0));
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

    Specification<TestCase> spec = buildTestCaseFlakinessSpecification(
        request, repositoryId);

    Page<TestCase> resultPage = testCaseRepository.findAll(spec, pageable);
    List<TestCase> flakyTests = resultPage.getContent();
    List<FlakyTestOverviewDto.FlakyTestDto> flakyTestDtos =
        flakyTests.stream().map(FlakyTestOverviewDto.FlakyTestDto::from).toList();

    FlakyTestOverviewDto.FlakyTestSummary summary = buildGlobalSummary(repositoryId);

    return new FlakyTestOverviewDto(summary, flakyTestDtos, resultPage.getTotalElements());
  }

  private FlakyTestOverviewDto.FlakyTestSummary buildGlobalSummary(Long repositoryId) {
    int totalTrackedTests = (int) testCaseRepository.countByRepositoryRepositoryId(repositoryId);
    int totalFlakyTests = (int) testCaseRepository
        .countByRepositoryRepositoryIdAndFlakinessScoreGreaterThan(repositoryId, 0);
    int highFlakinessCount = (int) testCaseRepository
        .countByRepositoryRepositoryIdAndFlakinessScoreGreaterThan(
            repositoryId, HIGH_FLAKINESS_THRESHOLD);
    int mediumFlakinessCount = (int) testCaseRepository
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

  private Specification<TestCase> buildTestCaseFlakinessSpecification(
      FlakyTestsPageRequest request, Long repositoryId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always scope to current repository
      predicates.add(cb.equal(root.get("repository").get("repositoryId"), repositoryId));

      // Retrieve only tests with a flakiness score greater than zero
      predicates.add(cb.greaterThan(root.get("flakinessScore"), 0));

      // Search term across name, className, and testSuiteName
      if (request.getSearchTerm() != null && !request.getSearchTerm().trim().isEmpty()) {
        String term = "%" + request.getSearchTerm().trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), term),
                cb.like(cb.lower(root.get("className")), term),
                cb.like(cb.lower(root.get("suiteName")), term)));
      }

      // Filter by flakiness score based on filter type
      switch (request.getFilterType()) {
        case HIGH:
          predicates.add(cb.greaterThan(root.get("flakinessScore"), HIGH_FLAKINESS_THRESHOLD));
          break;
        case MEDIUM:
          predicates.add(
              cb.and(
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
