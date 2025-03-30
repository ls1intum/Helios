package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestCase.TestStatus;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestResultService {
  private final WorkflowRunRepository workflowRunRepository;
  private final BranchRepository branchRepository;
  private final PullRequestRepository pullRequestRepository;
  private final TestCaseStatisticsService statisticsService;
  private final GitRepoRepository gitRepoRepository;

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
        workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryIdWithTestSuites(
            branchName, branch.getCommitSha(), repositoryId);

    var previousCommitSha =
        workflowRunRepository.findNthLatestCommitShaBehindHeadByBranchAndRepoId(
            branchName, repositoryId, 0, branch.getCommitSha());

    List<WorkflowRun> previousTestRuns =
        previousCommitSha.isEmpty()
            ? List.of()
            : workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryIdWithTestSuites(
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

    // Get repository ID from context
    final Long repositoryId = RepositoryContext.getRepositoryId();

    // Get repository by ID
    Optional<GitRepository> repositoryOpt = gitRepoRepository.findById(repositoryId);
    GitRepository repository = repositoryOpt.orElse(null);

    // Get statistics for the default branch if repository is available
    List<TestCaseStatistics> defaultBranchStats =
        repository != null
            ? statisticsService.getStatisticsForDefaultBranch(repository)
            : List.of();

    // Create a function to check if a test case with similar name exists in the default branch test
    // runs
    // and if it fails in the default branch
    Map<String, Boolean> defaultBranchFailures = getLatestDefaultBranchResults(repository);

    // Create statistics provider function
    Function<TestCase, TestResultsDto.TestCaseStatisticsInfo> statisticsProvider =
        testCase -> {
          // Check if test is flaky from statistics
          Optional<TestCaseStatistics> stats =
              defaultBranchStats.stream()
                  .filter(
                      s ->
                          s.getTestName().equals(testCase.getName())
                              && s.getClassName().equals(testCase.getClassName()))
                  .findFirst();

          boolean isFlaky = stats.map(TestCaseStatistics::isFlaky).orElse(false);
          double failureRate = stats.map(TestCaseStatistics::getFailureRate).orElse(0.0);

          // Check if test fails in default branch latest run
          String key = testCase.getClassName() + "." + testCase.getName();
          boolean failsInDefaultBranch = defaultBranchFailures.getOrDefault(key, false);

          return new TestResultsDto.TestCaseStatisticsInfo(
              isFlaky, failureRate, failsInDefaultBranch);
        };

    // Convert test suites to DTOs with statistics
    List<TestSuite> sortedTestSuites = new ArrayList<>();
    for (TestSuite suite : testSuites) {
      List<TestCase> sortedTestCases = sortTestCases(suite.getTestCases(), previousStatusProvider);
      suite.setTestCases(sortedTestCases);
      sortedTestSuites.add(suite);
    }

    List<TestResultsDto.TestSuiteDto> testSuiteDtos =
        sortedTestSuites.stream()
            .map(
                suite ->
                    TestResultsDto.TestSuiteDto.fromTestSuite(
                        suite, previousStatusProvider, statisticsProvider))
            .toList();

    return new TestResultsDto(testSuiteDtos, isProcessing);
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

              // If both failed, prioritize non-flaky failures and non-default branch failures
              if (failedA && failedB) {
                // Check if one is failing in default branch and the other is not
                boolean failsInDefault = a.isFailsInDefaultBranch();
                boolean otherFailsInDefault = b.isFailsInDefaultBranch();

                if (!failsInDefault && otherFailsInDefault) {
                  return -1; // a is more important (user's fault)
                }
                if (failsInDefault && !otherFailsInDefault) {
                  return 1; // b is more important (user's fault)
                }

                // Check if one is flaky and the other is not
                boolean flaky = a.isFlaky();
                boolean otherFlaky = b.isFlaky();

                if (!flaky && otherFlaky) {
                  return -1; // a is more important (not flaky)
                }
                if (flaky && !otherFlaky) {
                  return 1; // b is more important (not flaky)
                }
              }

              // 3. Third priority: Alphabetical sort for stable ordering
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
        workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryIdWithTestSuites(
            branchName, branch.getCommitSha(), repositoryId);
    var previousCommitSha =
        workflowRunRepository.findNthLatestCommitShaBehindHeadByBranchAndRepoId(
            branchName, repositoryId, 0, branch.getCommitSha());

    List<WorkflowRun> previousTestRuns =
        previousCommitSha.isEmpty()
            ? List.of()
            : workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryIdWithTestSuites(
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

    // Get repository ID from context
    final Long repositoryId = RepositoryContext.getRepositoryId();

    // Get repository by ID
    Optional<GitRepository> repositoryOpt = gitRepoRepository.findById(repositoryId);
    GitRepository repository = repositoryOpt.orElse(null);

    // Get statistics for the default branch if repository is available
    List<TestCaseStatistics> defaultBranchStats =
        repository != null
            ? statisticsService.getStatisticsForDefaultBranch(repository)
            : List.of();

    // Create a function to check if a test case with similar name exists in the default branch test
    // runs
    // and if it fails in the default branch
    Map<String, Boolean> defaultBranchFailures = getLatestDefaultBranchResults(repository);

    // Create statistics provider function
    Function<TestCase, TestResultsDto.TestCaseStatisticsInfo> statisticsProvider =
        testCase -> {
          // Check if test is flaky from statistics
          Optional<TestCaseStatistics> stats =
              defaultBranchStats.stream()
                  .filter(
                      s ->
                          s.getTestName().equals(testCase.getName())
                              && s.getClassName().equals(testCase.getClassName()))
                  .findFirst();

          boolean isFlaky = stats.map(TestCaseStatistics::isFlaky).orElse(false);
          double failureRate = stats.map(TestCaseStatistics::getFailureRate).orElse(0.0);

          // Check if test fails in default branch latest run
          String key = testCase.getClassName() + "." + testCase.getName();
          boolean failsInDefaultBranch = defaultBranchFailures.getOrDefault(key, false);

          return new TestResultsDto.TestCaseStatisticsInfo(
              isFlaky, failureRate, failsInDefaultBranch);
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
                      testSuite, previousStatusProvider, statisticsProvider);
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

  /**
   * Gets the latest test results from the default branch of a repository.
   *
   * @param repository the repository
   * @return map of test case keys to failure status
   */
  private Map<String, Boolean> getLatestDefaultBranchResults(GitRepository repository) {
    if (repository == null) {
      return Map.of();
    }
    // Get default branch name
    String defaultBranchName = repository.getDefaultBranch();
    // Find the default branch
    Optional<Branch> defaultBranch =
        branchRepository.findAll().stream()
            .filter(branch -> branch.getRepository().equals(repository) && branch.isDefault())
            .findFirst();
    if (defaultBranch.isEmpty()) {
      return Map.of();
    }
    // Get the latest workflow runs with test suites from the default branch
    List<WorkflowRun> defaultBranchRuns =
        workflowRunRepository.findByHeadBranchAndRepositoryIdWithTestSuites(
            defaultBranchName, repository.getRepositoryId());

    // Collect test cases and their status
    Map<String, Boolean> defaultBranchFailures = new HashMap<>();
    List<TestSuite> defaultBranchTestSuites = getTestSuitesForWorkflowRuns(defaultBranchRuns);

    for (TestSuite suite : defaultBranchTestSuites) {
      for (TestCase testCase : suite.getTestCases()) {
        String key = testCase.getClassName() + "." + testCase.getName();
        boolean isFailed =
            testCase.getStatus() == TestStatus.FAILED || testCase.getStatus() == TestStatus.ERROR;
        defaultBranchFailures.put(key, isFailed);
      }
    }

    return defaultBranchFailures;
  }
}
