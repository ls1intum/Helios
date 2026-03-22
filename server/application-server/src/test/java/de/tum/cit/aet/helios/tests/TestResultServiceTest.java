package de.tum.cit.aet.helios.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestCaseRun.TestStatus;
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
  @Mock private TestSuiteRunRepository testSuiteRunRepository;
  @Mock private TestCaseRunRepository testCaseRunRepository;

  @InjectMocks private TestResultService testResultService;

  private GitRepository gitRepository;
  private de.tum.cit.aet.helios.branch.Branch defaultBranch;
  private WorkflowRun workflowRun;
  private TestType testType;
  private TestSuiteRun testSuiteRun;
  private TestCaseRun testCaseRun;
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

    testCase = new TestCase();
    testCase.setId(1L);
    testCase.setName("test1");
    testCase.setClassName("TestClass1");
    testCase.setSuiteName("TestSuite1");

    testSuiteRun = new TestSuiteRun();
    testSuiteRun.setId(1L);
    testSuiteRun.setName("TestSuite1");
    testSuiteRun.setWorkflowRun(workflowRun);
    testSuiteRun.setTestType(testType);
    testSuiteRun.setTimestamp(LocalDateTime.now());
    testSuiteRun.setTests(1);
    testSuiteRun.setFailures(0);
    testSuiteRun.setErrors(0);
    testSuiteRun.setSkipped(0);
    testSuiteRun.setTime(0.0);

    testCaseRun = new TestCaseRun();
    testCaseRun.setId(1L);
    testCaseRun.setTestCase(testCase);
    testCaseRun.setTestSuiteRun(testSuiteRun);
    testCaseRun.setStatus(TestStatus.PASSED);
    testCaseRun.setTime(0.0);
    testSuiteRun.setTestCaseRuns(List.of(testCaseRun));
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
    when(testSuiteRunRepository.findByWorkflowRunIdAndTestTypeId(
            anyLong(), anyLong(), anyString(), anyBoolean(), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(testSuiteRun)));
    when(testSuiteRunRepository.findSummaryByWorkflowRunIdAndTestTypeId(
            anyLong(), anyLong()))
        .thenReturn(summary);
    when(testCaseRunRepository.findFailedByWorkflowRunIdAndSuiteNamesAndTestTypeId(
            any(), anyList(), anyLong()))
        .thenReturn(Collections.emptyList());

    TestResultsDto result =
        testResultService.getLatestTestResultsForBranch("featureBranch", criteria);

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
    when(testSuiteRunRepository.findByWorkflowRunIdAndTestTypeId(
            anyLong(), anyLong(), anyString(), anyBoolean(), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(testSuiteRun)));
    when(testSuiteRunRepository.findSummaryByWorkflowRunIdAndTestTypeId(
            anyLong(), anyLong()))
        .thenReturn(summary);
    when(testCaseRunRepository.findFailedByWorkflowRunIdAndSuiteNamesAndTestTypeId(
            any(), anyList(), anyLong()))
        .thenReturn(Collections.emptyList());

    TestResultsDto result = testResultService.getLatestTestResultsForPr(1L, criteria);

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
    TestCase tc1 = createTestCase("AlphaTest", "Class1", "Suite1", 0.0);
    TestCase tc2 = createTestCase("BetaTest", "Class2", "Suite1", 0.0);
    TestCase tc3 = createTestCase("CharlieTest", "Class3", "Suite1", 0.0);
    TestCase tc4 = createTestCase("DeltaTest", "Class4", "Suite1", 0.0);
    TestCase tc5 = createTestCase("EchoTest", "Class5", "Suite1", 0.0);
    TestCase tc6 = createTestCase("FoxtrotTest", "Class6", "Suite1", 50.0);

    TestCaseRun tcr1 = createTestCaseRun(tc1, TestStatus.PASSED, TestStatus.PASSED, false);
    TestCaseRun tcr2 = createTestCaseRun(tc2, TestStatus.FAILED, TestStatus.PASSED, false);
    TestCaseRun tcr3 = createTestCaseRun(tc3, TestStatus.FAILED, TestStatus.FAILED, false);
    TestCaseRun tcr4 = createTestCaseRun(tc4, TestStatus.PASSED, TestStatus.PASSED, false);
    TestCaseRun tcr5 = createTestCaseRun(tc5, TestStatus.FAILED, TestStatus.FAILED, true);
    TestCaseRun tcr6 = createTestCaseRun(tc6, TestStatus.FAILED, TestStatus.FAILED, false);

    List<TestCaseRun> testCaseRuns = new ArrayList<>(List.of(tcr1, tcr2, tcr3, tcr4, tcr5, tcr6));

    // Use reflection to access the private method
    Method sortMethod = TestResultService.class.getDeclaredMethod("sortTestCaseRuns", List.class);
    sortMethod.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<TestCaseRun> sortedTestCaseRuns =
        (List<TestCaseRun>) sortMethod.invoke(testResultService, testCaseRuns);

    // Expected order: Beta (status change), Charlie (failed, not default, not flaky), Foxtrot
    // (failed, not default, flaky), Echo (failed, default), Alpha (passed), Delta (passed)
    assertEquals("BetaTest", sortedTestCaseRuns.get(0).getTestCase().getName());
    assertEquals("CharlieTest", sortedTestCaseRuns.get(1).getTestCase().getName());
    assertEquals("FoxtrotTest", sortedTestCaseRuns.get(2).getTestCase().getName());
    assertEquals("EchoTest", sortedTestCaseRuns.get(3).getTestCase().getName());
    assertEquals("AlphaTest", sortedTestCaseRuns.get(4).getTestCase().getName());
    assertEquals("DeltaTest", sortedTestCaseRuns.get(5).getTestCase().getName());
  }

  @Test
  void filterTestCases_shouldFilterCorrectly() throws Exception {
    TestCase tc1 = createTestCase("MatchName", "SomeClass", "Suite1", 0);
    TestCase tc2 = createTestCase("AnotherTest", "MatchClass", "Suite1", 0);
    TestCase tc3 = createTestCase("NoMatch", "SomeOtherClass", "Suite1", 0);
    TestCase tc4 = createTestCase("FailedTest", "IrrelevantClass", "Suite1", 0);

    TestCaseRun tcr1 = createTestCaseRun(tc1, TestStatus.PASSED, null, false);
    TestCaseRun tcr2 = createTestCaseRun(tc2, TestStatus.FAILED, null, false);
    TestCaseRun tcr3 = createTestCaseRun(tc3, TestStatus.PASSED, null, false);
    TestCaseRun tcr4 = createTestCaseRun(tc4, TestStatus.FAILED, null, false);


    List<TestCaseRun> testCaseRuns = new ArrayList<>(List.of(tcr1, tcr2, tcr3, tcr4));

    Method filterMethod =
        TestResultService.class.getDeclaredMethod(
            "filterTestCaseRuns", List.class, String.class, boolean.class);
    filterMethod.setAccessible(true);

    // Test filtering by name
    @SuppressWarnings("unchecked")
    List<TestCaseRun> filteredByName =
        (List<TestCaseRun>) filterMethod.invoke(
            testResultService, testCaseRuns, "MatchName", false);
    assertEquals(1, filteredByName.size());
    assertEquals("MatchName", filteredByName.get(0).getTestCase().getName());

    // Test filtering by class name
    @SuppressWarnings("unchecked")
    List<TestCaseRun> filteredByClass =
        (List<TestCaseRun>) filterMethod.invoke(
            testResultService, testCaseRuns, "MatchClass", false);
    assertEquals(1, filteredByClass.size());
    assertEquals("MatchClass", filteredByClass.get(0).getTestCase().getClassName());

    // Test filtering by only failed
    @SuppressWarnings("unchecked")
    List<TestCaseRun> filteredByFailed =
        (List<TestCaseRun>) filterMethod.invoke(testResultService, testCaseRuns, "", true);
    assertEquals(2, filteredByFailed.size());
    assertTrue(
        filteredByFailed.stream()
            .allMatch(
                tcr ->
                    tcr.getStatus() == TestStatus.FAILED || tcr.getStatus() == TestStatus.ERROR));

    // Test filtering by name and only failed
    @SuppressWarnings("unchecked")
    List<TestCaseRun> filteredByNameAndFailed =
        (List<TestCaseRun>) filterMethod.invoke(
            testResultService, testCaseRuns, "AnotherTest", true);
    assertEquals(1, filteredByNameAndFailed.size());
    assertEquals("AnotherTest", filteredByNameAndFailed.get(0).getTestCase().getName());
    assertEquals(TestStatus.FAILED, filteredByNameAndFailed.get(0).getStatus());

    // Test with no filters
    @SuppressWarnings("unchecked")
    List<TestCaseRun> noFilters =
        (List<TestCaseRun>) filterMethod.invoke(testResultService, testCaseRuns, "", false);
    assertEquals(4, noFilters.size());
  }

  private TestCase createTestCase(
      String name, String className, String suiteName, double flakinessScore) {
    TestCase testCase = new TestCase();
    testCase.setName(name);
    testCase.setClassName(className);
    testCase.setSuiteName(suiteName);
    testCase.setFlakinessScore(flakinessScore);
    testCase.setRepository(gitRepository);
    return testCase;
  }

  private TestCaseRun createTestCaseRun(
      TestCase def, TestStatus status, TestStatus previousStatus, boolean failsInDefault) {
    TestCaseRun testCaseRun = new TestCaseRun();
    testCaseRun.setTestCase(def);
    testCaseRun.setStatus(status);
    testCaseRun.setPreviousStatus(previousStatus);
    testCaseRun.setFailsInDefaultBranch(failsInDefault);
    testCaseRun.setTime(0.0);
    return testCaseRun;
  }
}
