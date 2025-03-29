package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestCase.TestStatus;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class TestResultService {
  private final WorkflowRunRepository workflowRunRepository;
  private final BranchRepository branchRepository;
  private final PullRequestRepository pullRequestRepository;

  private List<TestSuite> getTestSuitesForWorkflowRuns(List<WorkflowRun> runs) {
    return runs.stream().flatMap(run -> run.getTestSuites().stream()).toList();
  }

  /**
   * Get the latest test results for a branch. This includes both Java and E2E test results.
   *
   * @param branchName the branch name
   * @return the test results
   */
  public TestResultsDto getLatestTestResultsForBranch(String branchName) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    var branch =
        branchRepository
            .findByNameAndRepositoryRepositoryId(branchName, repositoryId)
            .orElseThrow();

    var latestTestRuns =
        workflowRunRepository
            .findByHeadBranchAndHeadShaAndRepositoryIdAndPullRequestsIsNullWithTestSuites(
                branchName, branch.getCommitSha(), repositoryId);

    var previousCommitSha =
        workflowRunRepository.findNthLatestCommitShaBehindHeadByBranchAndRepoId(
            branchName, repositoryId, 0, branch.getCommitSha());

    List<WorkflowRun> previousTestRuns =
        previousCommitSha.isEmpty()
            ? List.of()
            : workflowRunRepository
                .findByHeadBranchAndHeadShaAndRepositoryIdAndPullRequestsIsNullWithTestSuites(
                    branchName, previousCommitSha.get(), repositoryId);

    return this.getResultsFromRuns(latestTestRuns, previousTestRuns);
  }

  /**
   * Get the latest test results for a pull request. This includes both Java and E2E test results.
   *
   * @param pullRequestId the pull request ID
   * @return the test results
   */
  public TestResultsDto getLatestTestResultsForPr(Long pullRequestId) {
    var pullRequest = pullRequestRepository.findById(pullRequestId).orElseThrow();

    var latestTestRuns =
        workflowRunRepository.findByPullRequestsIdAndHeadShaWithTestSuites(
            pullRequestId, pullRequest.getHeadSha());

    var previousCommitSha =
        workflowRunRepository.findNthLatestCommitShaBehindHeadByPullRequestId(
            pullRequestId, 0, pullRequest.getHeadSha());

    List<WorkflowRun> previousTestRuns =
        previousCommitSha.isEmpty()
            ? List.of()
            : workflowRunRepository.findByPullRequestsIdAndHeadShaWithTestSuites(
                pullRequestId, previousCommitSha.get());

    return this.getResultsFromRuns(latestTestRuns, previousTestRuns);
  }

  private TestResultsDto getResultsFromRuns(
      List<WorkflowRun> latestWorkflowRuns, List<WorkflowRun> previousWorkflowRuns) {

    var latestTestRuns =
        latestWorkflowRuns.stream()
            .filter(run -> run.getWorkflow().getLabel() == Workflow.Label.TEST)
            .toList();

    var previousTestRuns =
        previousWorkflowRuns.stream()
            .filter(run -> run.getWorkflow().getLabel() == Workflow.Label.TEST)
            .toList();

    var testSuites = getTestSuitesForWorkflowRuns(latestTestRuns);
    var previousTestCases =
        getTestSuitesForWorkflowRuns(previousTestRuns).stream()
            .flatMap(testSuite -> testSuite.getTestCases().stream())
            .toList();

    boolean isProcessing =
        latestTestRuns.stream()
            .anyMatch(
                run ->
                    run.getTestProcessingStatus() == WorkflowRun.TestProcessingStatus.PROCESSING);

    Function<TestCase, Optional<TestStatus>> previousStatusProvider =
        (testCase) -> {
          return previousTestCases.stream()
              .filter(
                  previousTestCase ->
                      previousTestCase.getName().equals(testCase.getName())
                          && previousTestCase.getClassName().equals(testCase.getClassName()))
              .findFirst()
              .map(TestCase::getStatus);
        };

    return new TestResultsDto(
        testSuites.stream()
            .map(
                testSuite -> {
                  // Sort test cases before creating the DTO
                  List<TestCase> sortedTestCases =
                      sortTestCases(testSuite.getTestCases(), previousStatusProvider);
                  testSuite.setTestCases(sortedTestCases);
                  return TestResultsDto.TestSuiteDto.fromTestSuite(
                      testSuite, previousStatusProvider);
                })
            .toList(),
        isProcessing);
  }

  /**
   * Sort test cases with the following priority: 1. Test cases with updated status 2. Failed or
   * error test cases 3. Alphabetical order by name for stable sorting
   *
   * @param testCases the test cases to sort
   * @param previousStatusProvider function to get previous status
   * @return sorted list of test cases
   */
  private List<TestCase> sortTestCases(
      List<TestCase> testCases, Function<TestCase, Optional<TestStatus>> previousStatusProvider) {

    return testCases.stream()
        .sorted(
            (a, b) -> {
              // Get previous statuses
              Optional<TestStatus> prevStatusA = previousStatusProvider.apply(a);
              Optional<TestStatus> prevStatusB = previousStatusProvider.apply(b);

              // Check for status changes
              boolean statusChangedA =
                  prevStatusA.isPresent() && a.getStatus() != prevStatusA.get();
              boolean statusChangedB =
                  prevStatusB.isPresent() && b.getStatus() != prevStatusB.get();

              // 1. First priority: Status changed
              if (statusChangedA && !statusChangedB) {
                return -1;
              }
              if (!statusChangedA && statusChangedB) {
                return 1;
              }

              // 2. Second priority: Failed or error status
              boolean failedA =
                  a.getStatus() == TestStatus.FAILED || a.getStatus() == TestStatus.ERROR;
              boolean failedB =
                  b.getStatus() == TestStatus.FAILED || b.getStatus() == TestStatus.ERROR;

              if (failedA && !failedB) {
                return -1;
              }
              if (!failedA && failedB) {
                return 1;
              }

              // 3. Third priority: Alphabetical by name for stable sorting
              return a.getName().compareTo(b.getName());
            })
        .collect(Collectors.toList());
  }

  /**
   * Get the latest test results for a branch, grouped by workflow. This enables separate display of
   * different test types (Java, E2E, etc.).
   *
   * @param branchName the branch name
   * @return the grouped test results
   */
  public GroupedTestResultsDto getLatestGroupedTestResultsForBranch(String branchName) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    var branch =
        branchRepository
            .findByNameAndRepositoryRepositoryId(branchName, repositoryId)
            .orElseThrow();

    var latestTestRuns =
        workflowRunRepository
            .findByHeadBranchAndHeadShaAndRepositoryIdAndPullRequestsIsNullWithTestSuites(
                branchName, branch.getCommitSha(), repositoryId);

    var previousCommitSha =
        workflowRunRepository.findNthLatestCommitShaBehindHeadByBranchAndRepoId(
            branchName, repositoryId, 0, branch.getCommitSha());

    List<WorkflowRun> previousTestRuns =
        previousCommitSha.isEmpty()
            ? List.of()
            : workflowRunRepository
                .findByHeadBranchAndHeadShaAndRepositoryIdAndPullRequestsIsNullWithTestSuites(
                    branchName, previousCommitSha.get(), repositoryId);

    return this.getGroupedResultsFromRuns(latestTestRuns, previousTestRuns);
  }

  /**
   * Get the latest test results for a pull request, grouped by workflow. This enables separate
   * display of different test types (Java, E2E, etc.).
   *
   * @param pullRequestId the pull request ID
   * @return the grouped test results
   */
  public GroupedTestResultsDto getLatestGroupedTestResultsForPr(Long pullRequestId) {
    var pullRequest = pullRequestRepository.findById(pullRequestId).orElseThrow();

    var latestTestRuns =
        workflowRunRepository.findByPullRequestsIdAndHeadShaWithTestSuites(
            pullRequestId, pullRequest.getHeadSha());

    var previousCommitSha =
        workflowRunRepository.findNthLatestCommitShaBehindHeadByPullRequestId(
            pullRequestId, 0, pullRequest.getHeadSha());

    List<WorkflowRun> previousTestRuns =
        previousCommitSha.isEmpty()
            ? List.of()
            : workflowRunRepository.findByPullRequestsIdAndHeadShaWithTestSuites(
                pullRequestId, previousCommitSha.get());

    return this.getGroupedResultsFromRuns(latestTestRuns, previousTestRuns);
  }

  /**
   * Create a GroupedTestResultsDto from the given workflow runs. Test suites are grouped by
   * workflow name.
   *
   * @param latestWorkflowRuns the latest workflow runs
   * @param previousWorkflowRuns the previous workflow runs for comparison
   * @return the grouped test results
   */
  private GroupedTestResultsDto getGroupedResultsFromRuns(
      List<WorkflowRun> latestWorkflowRuns, List<WorkflowRun> previousWorkflowRuns) {

    var latestTestRuns =
        latestWorkflowRuns.stream()
            .filter(run -> run.getWorkflow().getLabel() == Workflow.Label.TEST)
            .toList();

    var previousTestRuns =
        previousWorkflowRuns.stream()
            .filter(run -> run.getWorkflow().getLabel() == Workflow.Label.TEST)
            .toList();

    var testSuites = getTestSuitesForWorkflowRuns(latestTestRuns);
    var previousTestCases =
        getTestSuitesForWorkflowRuns(previousTestRuns).stream()
            .flatMap(testSuite -> testSuite.getTestCases().stream())
            .toList();

    boolean isProcessing =
        latestTestRuns.stream()
            .anyMatch(
                run ->
                    run.getTestProcessingStatus() == WorkflowRun.TestProcessingStatus.PROCESSING);

    Function<TestCase, Optional<TestStatus>> previousStatusProvider =
        (testCase) -> {
          return previousTestCases.stream()
              .filter(
                  previousTestCase ->
                      previousTestCase.getName().equals(testCase.getName())
                          && previousTestCase.getClassName().equals(testCase.getClassName()))
              .findFirst()
              .map(TestCase::getStatus);
        };

    // Convert test suites to DTOs
    List<TestResultsDto.TestSuiteDto> testSuiteDtos =
        testSuites.stream()
            .map(
                testSuite -> {
                  // Sort test cases before creating the DTO
                  List<TestCase> sortedTestCases =
                      sortTestCases(testSuite.getTestCases(), previousStatusProvider);
                  testSuite.setTestCases(sortedTestCases);
                  return TestResultsDto.TestSuiteDto.fromTestSuite(
                      testSuite, previousStatusProvider);
                })
            .toList();

    // Group test suites by workflow
    Map<String, List<TestResultsDto.TestSuiteDto>> groupedByWorkflow =
        testSuiteDtos.stream()
            .collect(
                Collectors.groupingBy(
                    dto -> dto.workflowName() != null ? dto.workflowName() : "Unknown",
                    Collectors.toList()));

    // Create the workflow results
    Map<String, GroupedTestResultsDto.WorkflowTestResults> testResults = new HashMap<>();
    for (var entry : groupedByWorkflow.entrySet()) {
      var workflowName = entry.getKey();
      var suites = entry.getValue();

      // Get the workflow ID from the first suite (all suites in this group have the same workflow)
      Long workflowId = suites.isEmpty() ? null : suites.get(0).workflowId();

      // Check if any test suite in this workflow is still processing
      boolean workflowIsProcessing =
          latestTestRuns.stream()
              .filter(run -> workflowName.equals(run.getWorkflow().getName()))
              .anyMatch(
                  run ->
                      run.getTestProcessingStatus() == WorkflowRun.TestProcessingStatus.PROCESSING);

      testResults.put(
          workflowName,
          new GroupedTestResultsDto.WorkflowTestResults(
              workflowId, workflowName, suites, workflowIsProcessing));
    }

    return new GroupedTestResultsDto(testResults, isProcessing);
  }
}
