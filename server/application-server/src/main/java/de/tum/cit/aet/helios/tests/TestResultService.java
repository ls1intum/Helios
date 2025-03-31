package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestCase.TestStatus;
import de.tum.cit.aet.helios.tests.TestResultsDto.TestTypeResults;
import de.tum.cit.aet.helios.tests.type.TestType;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
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
   * Sort test suites with the following priority: 1. Test suites with updates 2. Test suites with
   * failures or errors 3. Alphabetical order by name for stable sorting
   */
  private List<TestSuite> sortTestSuites(
      List<TestSuite> testSuites, Function<TestCase, Optional<TestStatus>> previousStatusProvider) {

    return testSuites.stream()
        .sorted(
            (a, b) -> {
              // 1. First priority: Has updates
              boolean hasUpdatesA = hasTestSuiteUpdates(a, previousStatusProvider);
              boolean hasUpdatesB = hasTestSuiteUpdates(b, previousStatusProvider);

              if (hasUpdatesA && !hasUpdatesB) {
                return -1;
              }
              if (!hasUpdatesA && hasUpdatesB) {
                return 1;
              }

              // 2. Second priority: Has failures or errors
              boolean hasFailuresA = a.getFailures() > 0 || a.getErrors() > 0;
              boolean hasFailuresB = b.getFailures() > 0 || b.getErrors() > 0;

              if (hasFailuresA && !hasFailuresB) {
                return -1;
              }
              if (!hasFailuresA && hasFailuresB) {
                return 1;
              }

              // 3. Third priority: Alphabetical by name for stable sorting
              return a.getName().compareTo(b.getName());
            })
        .collect(Collectors.toList());
  }

  /** Check if a test suite has any test cases with updated status */
  private boolean hasTestSuiteUpdates(
      TestSuite suite, Function<TestCase, Optional<TestStatus>> previousStatusProvider) {
    return suite.getTestCases().stream()
        .anyMatch(
            testCase -> {
              Optional<TestStatus> previousStatus = previousStatusProvider.apply(testCase);
              return previousStatus.isPresent() && testCase.getStatus() != previousStatus.get();
            });
  }

  /**
   * Get the latest test results for a branch, grouped by workflow. This enables separate display of
   * different test types (Java, E2E, etc.).
   *
   * @param branchName the branch name
   * @return the grouped test results
   */
  public TestResultsDto getLatestTestResultsForBranch(
      String branchName, int page, int size, String search, boolean onlyFailed) {
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

    return this.getResultsFromRuns(
        latestTestRuns, previousTestRuns, page, size, search, onlyFailed);
  }

  /**
   * Get the latest test results for a pull request, grouped by workflow. This enables separate
   * display of different test types (Java, E2E, etc.).
   *
   * @param pullRequestId the pull request ID
   * @return the grouped test results
   */
  public TestResultsDto getLatestTestResultsForPr(
      Long pullRequestId, int page, int size, String search, boolean onlyFailed) {
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

    return this.getResultsFromRuns(
        latestTestRuns, previousTestRuns, page, size, search, onlyFailed);
  }

  /**
   * Filter test suites based on search criteria and failed tests flag. Returns only test suites
   * that have matching test cases.
   */
  private List<TestResultsDto.TestSuiteDto> filterAndTransformTestSuites(
      List<TestSuite> suites,
      Function<TestCase, Optional<TestStatus>> previousStatusProvider,
      Function<TestCase, TestResultsDto.TestCaseStatisticsInfo> statisticsProvider,
      String search,
      boolean onlyFailed) {

    // First sort the test suites
    List<TestSuite> sortedSuites = sortTestSuites(suites, previousStatusProvider);

    return sortedSuites.stream()
        .map(
            suite -> {
              // First sort the test cases
              List<TestCase> sortedTestCases =
                  sortTestCases(suite.getTestCases(), previousStatusProvider);

              // Then filter the test cases based on search and onlyFailed criteria
              List<TestCase> filteredTestCases =
                  sortedTestCases.stream()
                      .filter(testCase -> matchesFilterCriteria(testCase, search, onlyFailed))
                      .collect(Collectors.toList());

              // Only create suite DTO if it has matching test cases
              if (filteredTestCases.isEmpty()) {
                return null;
              }

              // Create new suite with filtered test cases
              suite.setTestCases(filteredTestCases);
              return TestResultsDto.TestSuiteDto.fromTestSuite(
                  suite, previousStatusProvider, statisticsProvider);
            })
        .filter(suiteDto -> suiteDto != null) // Remove empty suites
        .collect(Collectors.toList());
  }

  /** Check if a test case matches the filter criteria */
  private boolean matchesFilterCriteria(TestCase testCase, String search, boolean onlyFailed) {
    boolean matchesSearch =
        search == null
            || search.isEmpty()
            || testCase.getName().toLowerCase().contains(search.toLowerCase())
            || testCase.getClassName().toLowerCase().contains(search.toLowerCase());

    boolean matchesFailedFilter =
        !onlyFailed
            || testCase.getStatus() == TestStatus.FAILED
            || testCase.getStatus() == TestStatus.ERROR;

    return matchesSearch && matchesFailedFilter;
  }

  private List<TestResultsDto.TestSuiteDto> paginateTestSuites(
      List<TestResultsDto.TestSuiteDto> suites, int page, int size) {
    int start = page * size;
    int end = Math.min(start + size, suites.size());

    return start < suites.size() ? suites.subList(start, end) : List.of();
  }

  /**
   * Create a TestResultsDto from the given workflow runs. Test suites are grouped by test type.
   *
   * @param latestWorkflowRuns the latest workflow runs
   * @param previousWorkflowRuns the previous workflow runs for comparison
   * @return the grouped test results
   */
  private TestResultsDto getResultsFromRuns(
      List<WorkflowRun> latestWorkflowRuns,
      List<WorkflowRun> previousWorkflowRuns,
      int page,
      int size,
      String search,
      boolean onlyFailed) {

    var latestTestRuns =
        latestWorkflowRuns.stream()
            .filter(run -> run.getWorkflow().getTestTypes().size() > 0)
            .toList();

    var previousTestRuns =
        previousWorkflowRuns.stream()
            .filter(run -> run.getWorkflow().getTestTypes().size() > 0)
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

    Map<TestType, List<TestSuite>> groupedByTestType =
        testSuites.stream()
            .filter(suite -> suite.getTestType() != null)
            .collect(Collectors.groupingBy(TestSuite::getTestType));

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

    List<TestTypeResults> testResults =
        groupedByTestType.entrySet().stream()
            .map(
                entry -> {
                  TestType testType = entry.getKey();
                  List<TestSuite> suites = entry.getValue();

                  // Calculate stats for this test type
                  int totalTests = 0;
                  int totalFailures = 0;
                  int totalErrors = 0;
                  int totalSkipped = 0;
                  double totalTime = 0;
                  int totalUpdates = 0;

                  for (TestSuite suite : suites) {
                    totalTests += suite.getTests();
                    totalFailures += suite.getFailures();
                    totalErrors += suite.getErrors();
                    totalSkipped += suite.getSkipped();
                    totalTime += suite.getTime();
                    totalUpdates +=
                        suite.getTestCases().stream()
                            .filter(
                                testCase -> {
                                  Optional<TestStatus> previousStatus =
                                      previousStatusProvider.apply(testCase);
                                  return previousStatus.isPresent()
                                      && testCase.getStatus() != previousStatus.get();
                                })
                            .count();
                  }

                  // Convert suites to DTOs with pagination
                  List<TestResultsDto.TestSuiteDto> filteredSuiteDtos =
                      filterAndTransformTestSuites(
                          suites, previousStatusProvider, statisticsProvider, search, onlyFailed);

                  // Apply pagination to filtered results
                  List<TestResultsDto.TestSuiteDto> paginatedSuites =
                      paginateTestSuites(filteredSuiteDtos, page, size);

                  // Check if this test type has any runs still processing
                  boolean testTypeProcessing =
                      latestTestRuns.stream()
                          .filter(
                              run ->
                                  run.getTestSuites().stream()
                                      .anyMatch(suite -> testType.equals(suite.getTestType())))
                          .anyMatch(
                              run ->
                                  run.getTestProcessingStatus()
                                      == WorkflowRun.TestProcessingStatus.PROCESSING);

                  return new TestTypeResults(
                      testType.getId(),
                      testType.getName(),
                      paginatedSuites,
                      testTypeProcessing,
                      new TestResultsDto.TestTypeStats(
                          suites.size(),
                          totalTests,
                          totalTests - (totalFailures + totalErrors),
                          totalFailures,
                          totalErrors,
                          totalSkipped,
                          totalTime,
                          totalUpdates));
                })
            .collect(Collectors.toList());

    return new TestResultsDto(testResults, isProcessing);
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
        workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryIdWithTestSuites(
            defaultBranchName, defaultBranch.get().getCommitSha(), repository.getRepositoryId());

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
