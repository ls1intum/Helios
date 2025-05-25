package de.tum.cit.aet.helios.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestCase.TestStatus;
import de.tum.cit.aet.helios.tests.type.TestType;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TestResultServiceTest {

  @Mock private WorkflowRunRepository workflowRunRepository;

  @Mock private BranchRepository branchRepository;

  @Mock private PullRequestRepository pullRequestRepository;

  @Mock private TestSuiteRepository testSuiteRepository;

  @Mock private TestCaseRepository testCaseRepository;

  @Mock private TestCaseStatisticsRepository testCaseStatisticsRepository;

  @Mock private TestCaseStatisticsService testCaseStatisticsService;

  @InjectMocks private TestResultService testResultService;

  private GitRepository gitRepository;
  private de.tum.cit.aet.helios.branch.Branch defaultBranch;
  private WorkflowRun workflowRun;
  private TestType testType;
  private TestSuite testSuite;
  private TestCase testCase;

  @BeforeEach
  void setUp() {
    gitRepository = new GitRepository();
    gitRepository.setRepositoryId(1L);
    RepositoryContext.setRepositoryId("1"); // Set repository context

    defaultBranch = new de.tum.cit.aet.helios.branch.Branch();
    defaultBranch.setName("main");
    defaultBranch.setCommitSha("defaultSha");
    defaultBranch.setRepository(gitRepository);
    defaultBranch.setDefault(true);

    workflowRun = new WorkflowRun();
    workflowRun.setId(1L);
    workflowRun.setHeadBranch("featureBranch");
    workflowRun.setHeadSha("featureSha");
    workflowRun.setRepository(gitRepository);
    final de.tum.cit.aet.helios.workflow.Workflow workflow =
        new de.tum.cit.aet.helios.workflow.Workflow();
    testType = new TestType();
    testType.setId(1L);
    testType.setName("Java");
    workflow.setTestTypes(Set.of(testType));
    workflowRun.setWorkflow(workflow);
    workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSED);

    testSuite = new TestSuite();
    testSuite.setId(1L);
    testSuite.setName("TestSuite1");
    testSuite.setWorkflowRun(workflowRun);
    testSuite.setTestType(testType);
    testSuite.setTimestamp(LocalDateTime.now());
    testSuite.setTests(1);
    testSuite.setFailures(0);
    testSuite.setErrors(0);
    testSuite.setSkipped(0);
    testSuite.setTime(0.0);

    testCase = new TestCase();
    testCase.setId(1L);
    testCase.setName("test1");
    testCase.setClassName("TestClass1");
    testCase.setStatus(TestStatus.PASSED);
    testCase.setTime(0.0);
    testSuite.setTestCases(List.of(testCase));
  }

  @AfterEach
  void tearDown() {
    RepositoryContext.clear(); // Clear repository context
  }

  @Test
  void getLatestTestResultsForBranch_shouldReturnResults() {
    final TestResultService.TestSearchCriteria criteria =
        new TestResultService.TestSearchCriteria(0, 10, "", false);
    de.tum.cit.aet.helios.branch.Branch featureBranch = new de.tum.cit.aet.helios.branch.Branch();
    featureBranch.setName("featureBranch");
    featureBranch.setCommitSha("featureSha");
    featureBranch.setRepository(gitRepository);

    when(branchRepository.findFirstByRepositoryRepositoryIdAndIsDefaultTrue(anyLong()))
        .thenReturn(Optional.of(defaultBranch));
    when(workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
            eq("main"), eq("defaultSha"), anyLong()))
        .thenReturn(List.of(workflowRun)); // Mock default branch runs
    when(branchRepository.findByNameAndRepositoryRepositoryId(eq("featureBranch"), anyLong()))
        .thenReturn(Optional.of(featureBranch));
    when(workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
            eq("featureBranch"), eq("featureSha"), anyLong()))
        .thenReturn(List.of(workflowRun));
    when(workflowRunRepository.findNthLatestCommitShaBehindHeadByBranchAndRepoId(
            anyString(), anyLong(), anyInt(), anyString()))
        .thenReturn(Optional.empty()); // No previous runs for simplicity

    TestSuiteSummaryDto summary = new TestSuiteSummaryDto(1L, 0L, 0L, 0L, 0.0, false);
    when(testSuiteRepository.findByWorkflowRunIdAndTestTypeId(
            anyLong(), anyLong(), any(), anyString(), anyBoolean(), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(testSuite)));
    when(testSuiteRepository.findSummaryByWorkflowRunIdAndTestTypeId(anyLong(), anyLong(), any()))
        .thenReturn(summary);
    when(testCaseRepository.findFailedByTestSuiteWorkflowIdAndClassNamesAndTestTypeId(
            any(), anyList(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(testCaseStatisticsRepository.findByTestSuiteNameInAndBranchNameAndRepositoryRepositoryId(
            anyList(), anyString(), anyLong()))
        .thenReturn(Collections.emptyList());
    lenient()
        .when(testCaseStatisticsService.calculateFlakinessScore(anyDouble(), anyDouble()))
        .thenReturn(0.0);

    TestResultsDto result =
        testResultService.getLatestTestResultsForBranch("featureBranch", criteria);

    // Verify the problematic call
    verify(testCaseStatisticsService).calculateFlakinessScore(0.0, 0.0);

    assertNotNull(result);
    assertFalse(result.testResults().isEmpty());
    assertEquals(1, result.testResults().size());
    TestResultsDto.TestTypeResults testTypeResults = result.testResults().get(0);
    assertEquals("Java", testTypeResults.testTypeName());
    assertFalse(testTypeResults.testSuites().isEmpty());
    assertEquals("TestSuite1", testTypeResults.testSuites().get(0).name());
    assertEquals(1, testTypeResults.testSuites().get(0).testCases().size());
    assertEquals("test1", testTypeResults.testSuites().get(0).testCases().get(0).name());
  }

  @Test
  void getLatestTestResultsForPr_shouldReturnResults() {
    final TestResultService.TestSearchCriteria criteria =
        new TestResultService.TestSearchCriteria(0, 10, "", false);
    de.tum.cit.aet.helios.pullrequest.PullRequest pullRequest =
        new de.tum.cit.aet.helios.pullrequest.PullRequest();
    pullRequest.setId(1L);
    pullRequest.setHeadSha("prSha");
    pullRequest.setRepository(gitRepository);

    when(pullRequestRepository.findById(1L)).thenReturn(Optional.of(pullRequest));
    when(branchRepository.findFirstByRepositoryRepositoryIdAndIsDefaultTrue(anyLong()))
        .thenReturn(Optional.of(defaultBranch));
    when(workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
            eq("main"), eq("defaultSha"), anyLong()))
        .thenReturn(List.of(workflowRun)); // Mock default branch runs
    when(workflowRunRepository.findByPullRequestsIdAndHeadSha(eq(1L), eq("prSha")))
        .thenReturn(List.of(workflowRun));
    when(workflowRunRepository.findNthLatestCommitShaBehindHeadByPullRequestId(
            anyLong(), anyInt(), anyString()))
        .thenReturn(Optional.empty()); // No previous runs for simplicity

    TestSuiteSummaryDto summary = new TestSuiteSummaryDto(1L, 0L, 0L, 0L, 0.0, false);
    when(testSuiteRepository.findByWorkflowRunIdAndTestTypeId(
            anyLong(), anyLong(), any(), anyString(), anyBoolean(), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(testSuite)));
    when(testSuiteRepository.findSummaryByWorkflowRunIdAndTestTypeId(anyLong(), anyLong(), any()))
        .thenReturn(summary);
    when(testCaseRepository.findFailedByTestSuiteWorkflowIdAndClassNamesAndTestTypeId(
            any(), anyList(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(testCaseStatisticsRepository.findByTestSuiteNameInAndBranchNameAndRepositoryRepositoryId(
            anyList(), anyString(), anyLong()))
        .thenReturn(Collections.emptyList());
    lenient()
        .when(testCaseStatisticsService.calculateFlakinessScore(anyDouble(), anyDouble()))
        .thenReturn(0.0);

    TestResultsDto result = testResultService.getLatestTestResultsForPr(1L, criteria);

    // Verify the problematic call
    verify(testCaseStatisticsService).calculateFlakinessScore(0.0, 0.0);

    assertNotNull(result);
    assertFalse(result.testResults().isEmpty());
    assertEquals(1, result.testResults().size());
    TestResultsDto.TestTypeResults testTypeResults = result.testResults().get(0);
    assertEquals("Java", testTypeResults.testTypeName());
    assertFalse(testTypeResults.testSuites().isEmpty());
    assertEquals("TestSuite1", testTypeResults.testSuites().get(0).name());
    assertEquals(1, testTypeResults.testSuites().get(0).testCases().size());
    assertEquals("test1", testTypeResults.testSuites().get(0).testCases().get(0).name());
  }

  @Test
  void sortTestCases_shouldSortCorrectly() throws Exception {
    TestCase tc1 = new TestCase();
    tc1.setName("AlphaTest");
    tc1.setStatus(TestStatus.PASSED);
    tc1.setPreviousStatus(TestStatus.PASSED);
    tc1.setFailsInDefaultBranch(false);
    tc1.setFlakinessScore(0.0);

    TestCase tc2 = new TestCase();
    tc2.setName("BetaTest");
    tc2.setStatus(TestStatus.FAILED);
    tc2.setPreviousStatus(TestStatus.PASSED); // Status changed
    tc2.setFailsInDefaultBranch(false);
    tc2.setFlakinessScore(0.0);

    TestCase tc3 = new TestCase();
    tc3.setName("CharlieTest");
    tc3.setStatus(TestStatus.FAILED);
    tc3.setPreviousStatus(TestStatus.FAILED);
    tc3.setFailsInDefaultBranch(false);
    tc3.setFlakinessScore(0.0);

    TestCase tc4 = new TestCase();
    tc4.setName("DeltaTest");
    tc4.setStatus(TestStatus.PASSED);
    tc4.setPreviousStatus(TestStatus.PASSED);
    tc4.setFailsInDefaultBranch(false);
    tc4.setFlakinessScore(0.0);

    TestCase tc5 = new TestCase();
    tc5.setName("EchoTest");
    tc5.setStatus(TestStatus.FAILED);
    tc5.setPreviousStatus(TestStatus.FAILED);
    tc5.setFailsInDefaultBranch(true); // Fails in default
    tc5.setFlakinessScore(0.0);

    TestCase tc6 = new TestCase();
    tc6.setName("FoxtrotTest");
    tc6.setStatus(TestStatus.FAILED);
    tc6.setPreviousStatus(TestStatus.FAILED);
    tc6.setFailsInDefaultBranch(false);
    tc6.setFlakinessScore(50.0); // Flaky

    List<TestCase> testCases = new ArrayList<>(List.of(tc1, tc2, tc3, tc4, tc5, tc6));

    // Use reflection to access the private method
    Method sortMethod = TestResultService.class.getDeclaredMethod("sortTestCases", List.class);
    sortMethod.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<TestCase> sortedTestCases =
        (List<TestCase>) sortMethod.invoke(testResultService, testCases);

    // Expected order: Beta (status change), Charlie (failed, not default, not flaky), Foxtrot
    // (failed, not default, flaky), Echo (failed, default), Alpha (passed), Delta (passed)
    assertEquals("BetaTest", sortedTestCases.get(0).getName());
    assertEquals("CharlieTest", sortedTestCases.get(1).getName());
    assertEquals("FoxtrotTest", sortedTestCases.get(2).getName());
    assertEquals("EchoTest", sortedTestCases.get(3).getName());
    assertEquals("AlphaTest", sortedTestCases.get(4).getName());
    assertEquals("DeltaTest", sortedTestCases.get(5).getName());
  }

  @Test
  void filterTestCases_shouldFilterCorrectly() throws Exception {
    TestCase tc1 = new TestCase();
    tc1.setName("MatchName");
    tc1.setClassName("SomeClass");
    tc1.setStatus(TestStatus.PASSED);

    TestCase tc2 = new TestCase();
    tc2.setName("AnotherTest");
    tc2.setClassName("MatchClass");
    tc2.setStatus(TestStatus.FAILED);

    TestCase tc3 = new TestCase();
    tc3.setName("NoMatch");
    tc3.setClassName("SomeOtherClass");
    tc3.setStatus(TestStatus.PASSED);

    TestCase tc4 = new TestCase();
    tc4.setName("FailedTest");
    tc4.setClassName("IrrelevantClass");
    tc4.setStatus(TestStatus.FAILED);

    List<TestCase> testCases = new ArrayList<>(List.of(tc1, tc2, tc3, tc4));

    Method filterMethod =
        TestResultService.class.getDeclaredMethod(
            "filterTestCases", List.class, String.class, boolean.class);
    filterMethod.setAccessible(true);

    // Test filtering by name
    @SuppressWarnings("unchecked")
    List<TestCase> filteredByName =
        (List<TestCase>) filterMethod.invoke(testResultService, testCases, "MatchName", false);
    assertEquals(1, filteredByName.size());
    assertEquals("MatchName", filteredByName.get(0).getName());

    // Test filtering by class name
    @SuppressWarnings("unchecked")
    List<TestCase> filteredByClass =
        (List<TestCase>) filterMethod.invoke(testResultService, testCases, "MatchClass", false);
    assertEquals(1, filteredByClass.size());
    assertEquals("MatchClass", filteredByClass.get(0).getClassName());

    // Test filtering by only failed
    @SuppressWarnings("unchecked")
    List<TestCase> filteredByFailed =
        (List<TestCase>) filterMethod.invoke(testResultService, testCases, "", true);
    assertEquals(2, filteredByFailed.size());
    assertTrue(
        filteredByFailed.stream()
            .allMatch(
                tc -> tc.getStatus() == TestStatus.FAILED || tc.getStatus() == TestStatus.ERROR));

    // Test filtering by name and only failed
    @SuppressWarnings("unchecked")
    List<TestCase> filteredByNameAndFailed =
        (List<TestCase>) filterMethod.invoke(testResultService, testCases, "AnotherTest", true);
    assertEquals(1, filteredByNameAndFailed.size());
    assertEquals("AnotherTest", filteredByNameAndFailed.get(0).getName());
    assertEquals(TestStatus.FAILED, filteredByNameAndFailed.get(0).getStatus());

    // Test with no filters
    @SuppressWarnings("unchecked")
    List<TestCase> noFilters =
        (List<TestCase>) filterMethod.invoke(testResultService, testCases, "", false);
    assertEquals(4, noFilters.size());
  }
}
