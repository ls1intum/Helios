package de.tum.cit.aet.helios.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsFilterType;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsPageRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class TestCaseStatisticsServiceTest {

  @Mock private TestCaseStatisticsRepository statisticsRepository;
  @Mock private TestCaseFlakinessRepository flakinessRepository;

  @InjectMocks private TestCaseStatisticsService service;

  private GitRepository repository;

  @BeforeEach
  void setUp() {
    repository = new GitRepository();
    repository.setRepositoryId(1L);
  }

  @Test
  void getFlakyTestsOverview_returnsPagedOverview() {
    List<TestCaseFlakiness> flakinessList =
        IntStream.range(0, 5)
            .mapToObj(i -> createFlakiness("test" + i, "ClassA", "SuiteA", 82.0, 0.05, 0.10))
            .toList();

    Page<TestCaseFlakiness> page = new PageImpl<>(
        flakinessList,
        Pageable.ofSize(5), 10);

    when(flakinessRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    when(statisticsRepository.countByBranchNameAndRepositoryRepositoryId(
        "combined", repository.getRepositoryId()))
        .thenReturn(20L);

    when(flakinessRepository.countByRepositoryRepositoryId(repository.getRepositoryId()))
        .thenReturn(5L);

    when(flakinessRepository.countByRepositoryRepositoryIdAndFlakinessScoreGreaterThan(
        repository.getRepositoryId(), TestCaseStatisticsService.HIGH_FLAKINESS_THRESHOLD))
        .thenReturn(2L);

    when(flakinessRepository
        .countByRepositoryRepositoryIdAndFlakinessScoreGreaterThanAndFlakinessScoreLessThanEqual(
            repository.getRepositoryId(),
            TestCaseStatisticsService.LOW_FLAKINESS_THRESHOLD,
            TestCaseStatisticsService.HIGH_FLAKINESS_THRESHOLD))
        .thenReturn(2L);

    FlakyTestsPageRequest request = FlakyTestsPageRequest.builder()
        .page(1)
        .size(5)
        .filterType(FlakyTestsFilterType.ALL)
        .build();

    FlakyTestOverviewDto result =
        service.getFlakyTestsOverview(repository.getRepositoryId(), request);

    assertEquals(5, result.flakyTests().size());
    assertEquals(10, result.filteredCount());

    FlakyTestOverviewDto.FlakyTestDto dto = result.flakyTests().getFirst();

    assertAll(
        () -> assertEquals("test0", dto.testName()),
        () -> assertEquals("ClassA", dto.className()),
        () -> assertEquals("SuiteA", dto.testSuiteName()),
        () -> assertEquals(82.0, dto.flakinessScore())
    );

    FlakyTestOverviewDto.FlakyTestSummary summary = result.summary();

    assertAll(
        () -> assertEquals(20, summary.totalTrackedTests()),
        () -> assertEquals(5, summary.flakyTestCount()),
        () -> assertEquals(2, summary.highFlakinessCount()),
        () -> assertEquals(2, summary.mediumFlakinessCount()),
        () -> assertEquals(1, summary.lowFlakinessCount())
    );
  }

  @Test
  void getFlakinessScoresForTests_returnsScores() {
    TestCaseFlakiness flakiness = createFlakiness("test1", "Class1", "Suite1", 88.2, 0.05, 0.08);
    when(flakinessRepository.findByRepositoryIdAndTestNameAndClassName(1L, "test1", "Class1"))
        .thenReturn(List.of(flakiness));

    TestFlakinessScoreRequest.TestCaseIdentifier identifier =
        new TestFlakinessScoreRequest.TestCaseIdentifier("test1", "Class1");

    List<TestFlakinessScoreDto> result =
        service.getFlakinessScoresForTests(1L, List.of(identifier));

    assertEquals(1, result.size());
    assertEquals("test1", result.getFirst().testName());
    assertEquals("Class1", result.getFirst().className());
    assertEquals(0.05, result.getFirst().defaultBranchFailureRate());
    assertEquals(0.08, result.getFirst().combinedFailureRate());
    assertEquals(88.2, result.getFirst().flakinessScore());
  }

  @Test
  void getFlakinessScoresForTests_returnsZeroDtoWhenNoMatchExists() {
    when(flakinessRepository.findByRepositoryIdAndTestNameAndClassName(1L, "missing", "Class1"))
        .thenReturn(List.of());

    TestFlakinessScoreRequest.TestCaseIdentifier identifier =
        new TestFlakinessScoreRequest.TestCaseIdentifier("missing", "Class1");

    List<TestFlakinessScoreDto> result =
        service.getFlakinessScoresForTests(1L, List.of(identifier));

    assertEquals(1, result.size());
    assertAll(
        () -> assertEquals("missing", result.getFirst().testName()),
        () -> assertEquals("Class1", result.getFirst().className()),
        () -> assertEquals(0.0, result.getFirst().defaultBranchFailureRate()),
        () -> assertEquals(0.0, result.getFirst().combinedFailureRate()),
        () -> assertEquals(0.0, result.getFirst().flakinessScore()));
  }

  @Test
  void calculateFlakinessScore_withFlakyRates_returnsPositiveScore() {
    double score = service.calculateFlakinessScore(0.05, 0.08);
    assertEquals(88.2, score, 0.1);
  }

  @Test
  void calculateFlakinessScore_withZeroRates_returnsZero() {
    double score = service.calculateFlakinessScore(0.0, 0.0);
    assertEquals(0.0, score);
  }

  @Test
  void computeFlakinessInfo_withMatchingStats_returnsInfo() {
    var defaultStat = createStat("test1", "Class1", "Suite1", "main", 100, 5);
    var combinedStat = createStat("test1", "Class1", "Suite1", "combined", 200, 30);

    var info =
        service.computeFlakinessInfo(
            "test1", "Class1", List.of(defaultStat), List.of(combinedStat));

    assertEquals(0.05, info.defaultBranchFailureRate());
    assertEquals(0.15, info.combinedFailureRate());
    assertEquals(84.0, info.flakinessScore(), 0.1);
  }

  @Test
  void computeFlakinessInfo_withNoMatchingStats_returnsZeroRates() {
    var info = service.computeFlakinessInfo("unknown", "Unknown", List.of(), List.of());

    assertEquals(0.0, info.defaultBranchFailureRate());
    assertEquals(0.0, info.combinedFailureRate());
    assertEquals(0.0, info.flakinessScore());
  }

  private TestCaseFlakiness createFlakiness(
      String testName,
      String className,
      String suiteName,
      double score,
      double defaultRate,
      double combinedRate) {
    TestCaseFlakiness flakiness = new TestCaseFlakiness();
    flakiness.setTestName(testName);
    flakiness.setClassName(className);
    flakiness.setTestSuiteName(suiteName);
    flakiness.setFlakinessScore(score);
    flakiness.setDefaultBranchFailureRate(defaultRate);
    flakiness.setCombinedFailureRate(combinedRate);
    flakiness.setRepository(repository);
    flakiness.setLastUpdated(OffsetDateTime.now());
    return flakiness;
  }

  private static TestCaseStatistics createStat(
      String testName, String className, String suiteName,
      String branchName, int total, int failed) {
    var stat = new TestCaseStatistics();
    stat.setTestName(testName);
    stat.setClassName(className);
    stat.setTestSuiteName(suiteName);
    stat.setBranchName(branchName);
    stat.setTotalRuns(total);
    stat.setFailedRuns(failed);
    stat.setLastUpdated(OffsetDateTime.now());
    return stat;
  }
}
