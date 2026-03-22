package de.tum.cit.aet.helios.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsFilterType;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsPageRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
  @Mock private TestCaseRepository testCaseRepository;

  @InjectMocks private TestCaseStatisticsService service;

  private GitRepository repository;

  @BeforeEach
  void setUp() {
    repository = new GitRepository();
    repository.setRepositoryId(1L);
  }

  @Test
  void getFlakyTestsOverview_returnsPagedOverview() {
    List<TestCase> testCases =
        IntStream.range(0, 5)
            .mapToObj(i -> createTestCase("test" + i, "ClassA", "SuiteA", 82.0, 0.05, 0.10))
            .toList();

    Page<TestCase> page = new PageImpl<>(
        testCases,
        Pageable.ofSize(5), 10);

    when(testCaseRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    when(testCaseRepository.countByRepositoryRepositoryId(repository.getRepositoryId()))
        .thenReturn(20L);

    when(testCaseRepository.countByRepositoryRepositoryIdAndFlakinessScoreGreaterThan(
        repository.getRepositoryId(), 0))
        .thenReturn(5L);

    when(testCaseRepository.countByRepositoryRepositoryIdAndFlakinessScoreGreaterThan(
        repository.getRepositoryId(), TestCaseStatisticsService.HIGH_FLAKINESS_THRESHOLD))
        .thenReturn(2L);

    when(testCaseRepository
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
    TestCase testCase = createTestCase("test1", "Class1", "Suite1", 88.2, 0.05, 0.08);
    when(testCaseRepository.findByRepositoryRepositoryIdAndSuiteNameAndClassNameAndName(
        1L, "Suite1", "Class1", "test1"))
        .thenReturn(Optional.of(testCase));

    TestFlakinessScoreRequest.TestCaseIdentifier identifier =
        new TestFlakinessScoreRequest.TestCaseIdentifier("test1", "Class1", "Suite1");

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
    when(testCaseRepository.findByRepositoryRepositoryIdAndSuiteNameAndClassNameAndName(
        1L, "Suite1", "Class1", "missing"))
        .thenReturn(Optional.empty());

    TestFlakinessScoreRequest.TestCaseIdentifier identifier =
        new TestFlakinessScoreRequest.TestCaseIdentifier("missing", "Class1", "Suite1");

    List<TestFlakinessScoreDto> result =
        service.getFlakinessScoresForTests(1L, List.of(identifier));

    assertEquals(1, result.size());
    assertAll(
        () -> assertEquals("missing", result.getFirst().testName()),
        () -> assertEquals("Class1", result.getFirst().className()),
        () -> assertEquals("Suite1", result.getFirst().testSuiteName()),
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

  private TestCase createTestCase(
      String testName,
      String className,
      String suiteName,
      double score,
      double defaultRate,
      double combinedRate) {
    TestCase testCase = new TestCase();
    testCase.setName(testName);
    testCase.setClassName(className);
    testCase.setSuiteName(suiteName);
    testCase.setFlakinessScore(score);
    testCase.setDefaultBranchFailureRate(defaultRate);
    testCase.setCombinedFailureRate(combinedRate);
    testCase.setRepository(repository);
    testCase.setUpdatedAt(OffsetDateTime.now());
    return testCase;
  }
}
