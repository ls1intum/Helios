package de.tum.cit.aet.helios.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestCaseStatisticsServiceTest {

  @Mock private TestCaseStatisticsRepository statisticsRepository;
  @Mock private BranchRepository branchRepository;

  @InjectMocks private TestCaseStatisticsService service;

  private GitRepository repository;
  private Branch defaultBranch;

  @BeforeEach
  void setUp() {
    repository = new GitRepository();
    repository.setRepositoryId(1L);

    defaultBranch = new Branch();
    defaultBranch.setName("main");
    defaultBranch.setRepository(repository);
    defaultBranch.setDefault(true);
  }

  @Test
  void getFlakyTestsOverview_withCombinedAndDefaultStats_returnsSortedOverview() {
    var combinedStat = createStat("testFlaky", "FlakyClass", "Suite1", "combined", 100, 5);
    var defaultStat = createStat("testFlaky", "FlakyClass", "Suite1", "main", 50, 2);

    when(branchRepository.findFirstByRepositoryRepositoryIdAndIsDefaultTrue(1L))
        .thenReturn(Optional.of(defaultBranch));
    when(statisticsRepository.findByBranchNameAndRepositoryRepositoryId("main", 1L))
        .thenReturn(List.of(defaultStat));
    when(statisticsRepository.findByBranchNameAndRepositoryRepositoryId("combined", 1L))
        .thenReturn(List.of(combinedStat));

    FlakyTestOverviewDto result = service.getFlakyTestsOverview(1L);

    assertEquals(1, result.summary().totalTrackedTests());
    assertEquals(1, result.summary().flakyTestCount());
    assertEquals(1, result.flakyTests().size());
    assertEquals("testFlaky", result.flakyTests().get(0).testName());
    assertEquals("FlakyClass", result.flakyTests().get(0).className());
    assertEquals("Suite1", result.flakyTests().get(0).testSuiteName());
    assertEquals(100, result.flakyTests().get(0).totalRuns());
    assertEquals(5, result.flakyTests().get(0).failedRuns());
  }

  @Test
  void getFlakyTestsOverview_withNoStats_returnsEmptyOverview() {
    when(branchRepository.findFirstByRepositoryRepositoryIdAndIsDefaultTrue(1L))
        .thenReturn(Optional.of(defaultBranch));
    when(statisticsRepository.findByBranchNameAndRepositoryRepositoryId("main", 1L))
        .thenReturn(List.of());
    when(statisticsRepository.findByBranchNameAndRepositoryRepositoryId("combined", 1L))
        .thenReturn(List.of());

    FlakyTestOverviewDto result = service.getFlakyTestsOverview(1L);

    assertEquals(0, result.summary().totalTrackedTests());
    assertEquals(0, result.summary().flakyTestCount());
    assertEquals(0, result.flakyTests().size());
  }

  @Test
  void getFlakyTestsOverview_withNoDefaultBranch_throws() {
    when(branchRepository.findFirstByRepositoryRepositoryIdAndIsDefaultTrue(1L))
        .thenReturn(Optional.empty());

    assertThrows(Exception.class, () -> service.getFlakyTestsOverview(1L));
  }

  @Test
  void getFlakinessScoresForTests_returnsScores() {
    var combinedStat = createStat("test1", "Class1", "Suite1", "combined", 100, 5);
    var defaultStat = createStat("test1", "Class1", "Suite1", "main", 50, 2);
    var identifier = new TestFlakinessScoreRequest.TestCaseIdentifier("test1", "Class1");

    when(branchRepository.findFirstByRepositoryRepositoryIdAndIsDefaultTrue(1L))
        .thenReturn(Optional.of(defaultBranch));
    when(statisticsRepository.findByBranchNameAndRepositoryRepositoryId("main", 1L))
        .thenReturn(List.of(defaultStat));
    when(statisticsRepository.findByBranchNameAndRepositoryRepositoryId("combined", 1L))
        .thenReturn(List.of(combinedStat));

    List<TestFlakinessScoreDto> result =
        service.getFlakinessScoresForTests(1L, List.of(identifier));

    assertEquals(1, result.size());
    assertEquals("test1", result.getFirst().testName());
    assertEquals("Class1", result.getFirst().className());
    assertEquals(0.04, result.getFirst().defaultBranchFailureRate());
    assertEquals(0.05, result.getFirst().combinedFailureRate());
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
    var combinedStat = createStat("test1", "Class1", "Suite1", "combined", 200, 10);

    var info =
        service.computeFlakinessInfo(
            "test1", "Class1", List.of(defaultStat), List.of(combinedStat));

    assertEquals(0.05, info.defaultBranchFailureRate());
    assertEquals(0.05, info.combinedFailureRate());
  }

  @Test
  void computeFlakinessInfo_withNoMatchingStats_returnsZeroRates() {
    var info = service.computeFlakinessInfo("unknown", "Unknown", List.of(), List.of());

    assertEquals(0.0, info.defaultBranchFailureRate());
    assertEquals(0.0, info.combinedFailureRate());
    assertEquals(0.0, info.flakinessScore());
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
