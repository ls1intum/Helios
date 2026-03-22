package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestCaseRun.TestStatus;
import de.tum.cit.aet.helios.tests.TestResultsDto.TestCaseDto;
import de.tum.cit.aet.helios.tests.TestResultsDto.TestTypeResults;
import de.tum.cit.aet.helios.tests.type.TestType;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class TestResultService {
  private final WorkflowRunRepository workflowRunRepository;
  private final BranchRepository branchRepository;
  private final PullRequestRepository pullRequestRepository;
  private final TestSuiteRunRepository testSuiteRunRepository;
  private final TestCaseRunRepository testCaseRunRepository;

  public record TestSearchCriteria(int page, int size, String search, boolean onlyFailed) {}

  private record TestRunContext(
      List<WorkflowRun> latestRuns,
      List<WorkflowRun> previousRuns,
      String defaultBranchName,
      Map<TestType, Long> defaultWorkflowRunByTestType) {}

  /**
   * Sort test case runs with the following priority: 1. Test cases with updated status 2. Failed or
   * error test cases 3. Alphabetical order by test case name for stable sorting
   *
   * @param testCaseRuns the test case runs to sort
   * @param previousStatusProvider function to get previous status
   * @return sorted list of test case runs
   */
  private List<TestCaseRun> sortTestCaseRuns(List<TestCaseRun> testCaseRuns) {
    return testCaseRuns.stream()
        .sorted(
            (a, b) -> {
              // Get previous statuses
              Optional<TestStatus> prevStatusA = Optional.ofNullable(a.getPreviousStatus());
              Optional<TestStatus> prevStatusB = Optional.ofNullable(b.getPreviousStatus());

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
                boolean flaky = a.getTestCase().getFlakinessScore() > 30.0;
                boolean otherFlaky = b.getTestCase().getFlakinessScore() > 30.0;

                if (!flaky && otherFlaky) {
                  return -1; // a is more important (not flaky)
                }
                if (flaky && !otherFlaky) {
                  return 1; // b is more important (not flaky)
                }
              }

              // 3. Third priority: Alphabetical sort for stable ordering
              return a.getTestCase().getName().compareTo(b.getTestCase().getName());
            })
        .collect(Collectors.toList());
  }

  private TestRunContext getDefaultBranchContext(GitRepository repository) {
    var defaultBranch =
        branchRepository
            .findFirstByRepositoryRepositoryIdAndIsDefaultTrue(repository.getRepositoryId())
            .orElseThrow();

    var defaultRuns =
        workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
            defaultBranch.getName(), defaultBranch.getCommitSha(), repository.getRepositoryId());

    Map<TestType, Long> defaultWorkflowRunByTestType = new HashMap<>();

    for (WorkflowRun run : defaultRuns) {
      for (TestType type : run.getWorkflow().getTestTypes()) {
        defaultWorkflowRunByTestType.put(type, run.getId());
      }
    }

    return new TestRunContext(
        defaultRuns, List.of(), defaultBranch.getName(), defaultWorkflowRunByTestType);
  }

  /**
   * Get the latest test results for a branch, grouped by workflow. This enables separate display of
   * different test types (Java, E2E, etc.).
   *
   * @param branchName the branch name
   * @return the grouped test results
   */
  public TestResultsDto getLatestTestResultsForBranch(
      String branchName, TestSearchCriteria criteria) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    var branch =
        branchRepository
            .findByNameAndRepositoryRepositoryId(branchName, repositoryId)
            .orElseThrow();

    var defaultContext = getDefaultBranchContext(branch.getRepository());

    var latestRuns =
        workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
            branchName, branch.getCommitSha(), repositoryId);

    var previousCommitSha =
        workflowRunRepository.findNthLatestCommitShaBehindHeadByBranchAndRepoId(
            branchName, repositoryId, 0, branch.getCommitSha());

    List<WorkflowRun> previousRuns =
        previousCommitSha.isEmpty()
            ? List.of()
            : workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
                branchName, previousCommitSha.get(), repositoryId);

    var context =
        new TestRunContext(
            latestRuns,
            previousRuns,
            defaultContext.defaultBranchName(),
            defaultContext.defaultWorkflowRunByTestType());

    return processTestResults(context, criteria);
  }

  /**
   * Get the latest test results for a pull request, grouped by workflow. This enables separate
   * display of different test types (Java, E2E, etc.).
   *
   * @param pullRequestId the pull request ID
   * @return the grouped test results
   */
  public TestResultsDto getLatestTestResultsForPr(Long pullRequestId, TestSearchCriteria criteria) {
    var pullRequest = pullRequestRepository.findById(pullRequestId).orElseThrow();
    var defaultContext = getDefaultBranchContext(pullRequest.getRepository());

    var latestRuns =
        workflowRunRepository.findByPullRequestsIdAndHeadSha(
            pullRequestId, pullRequest.getHeadSha());

    var previousCommitSha =
        workflowRunRepository.findNthLatestCommitShaBehindHeadByPullRequestId(
            pullRequestId, 0, pullRequest.getHeadSha());

    List<WorkflowRun> previousRuns =
        previousCommitSha.isEmpty()
            ? List.of()
            : workflowRunRepository.findByPullRequestsIdAndHeadSha(
                pullRequestId, previousCommitSha.get());

    var context =
        new TestRunContext(
            latestRuns,
            previousRuns,
            defaultContext.defaultBranchName(),
            defaultContext.defaultWorkflowRunByTestType());

    return processTestResults(context, criteria);
  }

  private TestResultsDto processTestResults(TestRunContext context, TestSearchCriteria criteria) {
    Map<TestType, Long> previousWorkflowRunByTestType = new HashMap<>();

    for (WorkflowRun run : context.previousRuns()) {
      for (TestType type : run.getWorkflow().getTestTypes()) {
        previousWorkflowRunByTestType.put(type, run.getId());
      }
    }

    List<TestTypeResults> results = new LinkedList<>();

    for (WorkflowRun run : context.latestRuns()) {
      for (TestType type : run.getWorkflow().getTestTypes()) {
        var testTypeResults =
            getTestTypeResultsForRun(
                type,
                run,
                previousWorkflowRunByTestType.get(type),
                PageRequest.of(criteria.page(), criteria.size()),
                context.defaultWorkflowRunByTestType().get(type),
                criteria.search(),
                criteria.onlyFailed());
        results.add(testTypeResults);
      }
    }

    var anyProcessing = results.stream().anyMatch(TestTypeResults::isProcessing);
    return new TestResultsDto(results, anyProcessing);
  }

  private List<TestCaseRun> filterTestCaseRuns(
      List<TestCaseRun> testCaseRuns, String search, boolean onlyFailed) {
    return testCaseRuns.stream()
        .filter(
            testCaseRun -> {
              // Filter by failed status if onlyFailed is true
              if (onlyFailed) {
                if (testCaseRun.getStatus() != TestStatus.FAILED
                    && testCaseRun.getStatus() != TestStatus.ERROR) {
                  return false;
                }
              }

              // Filter by search term if search is not null or empty
              if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                return testCaseRun.getTestCase().getName().toLowerCase().contains(searchLower)
                    || testCaseRun.getTestCase().getClassName().toLowerCase().contains(searchLower);
              }

              return true;
            })
        .collect(Collectors.toList());
  }

  private TestTypeResults getTestTypeResultsForRun(
      TestType type,
      WorkflowRun run,
      Long prevWorkflowRunId,
      Pageable pageable,
      Long defaultWorkflowRunId,
      String search,
      boolean onlyFailed) {

    log.debug(
        "Getting test results for type {} in workflow run {} with previous run {}",
        type.getName(),
        run.getId(),
        prevWorkflowRunId);
    long time = System.currentTimeMillis();

    var suiteRuns =
        testSuiteRunRepository.findByWorkflowRunIdAndTestTypeId(
            run.getId(), type.getId(), search, onlyFailed, pageable);

    log.debug(
        "Found {} test suites in {} ms",
        suiteRuns.getTotalElements(),
        System.currentTimeMillis() - time);

    var summary =
        testSuiteRunRepository.findSummaryByWorkflowRunIdAndTestTypeId(
            run.getId(), type.getId());

    var suiteNames = suiteRuns.stream().map(TestSuiteRun::getName).distinct().toList();

    var failedTestsInDefault =
        testCaseRunRepository.findFailedByWorkflowRunIdAndSuiteNamesAndTestTypeId(
            defaultWorkflowRunId, suiteNames, type.getId());

    var prevStateCandidates =
        testCaseRunRepository.findByWorkflowRunIdAndSuiteNamesAndTestTypeId(
            prevWorkflowRunId, suiteNames, type.getId());

    // "test failed on default branch" set - O(1) contains() instead of O(n) anyMatch()
    Set<Long> failedInDefaultTestCaseIds =
        failedTestsInDefault.stream()
            .map(tcr -> tcr.getTestCase().getId())
            .collect(Collectors.toSet());

    // Previous-status map - O(1) get() instead of O(n) filter().findFirst()
    Map<Long, TestStatus> prevStatusByTestCaseId =
        prevStateCandidates.stream()
            .collect(
                Collectors.toMap(
                    tcr -> tcr.getTestCase().getId(),
                    TestCaseRun::getStatus,
                    (a, b) -> a));

    for (TestSuiteRun suite : suiteRuns) {
      for (TestCaseRun testCaseRun : suite.getTestCaseRuns()) {
        Long testCaseId = testCaseRun.getTestCase().getId();
        testCaseRun.setFailsInDefaultBranch(failedInDefaultTestCaseIds.contains(testCaseId));
        testCaseRun.setPreviousStatus(prevStatusByTestCaseId.get(testCaseId));
      }
    }

    var suiteDtos =
        suiteRuns.stream()
            .map(
                suite -> {
                  var filteredTestCases =
                      filterTestCaseRuns(suite.getTestCaseRuns(), search, onlyFailed);
                  var testCases = sortTestCaseRuns(filteredTestCases);
                  var testCaseDtos = testCases.stream().map(TestCaseDto::fromTestCase).toList();

                  return TestResultsDto.TestSuiteDto.fromTestSuiteRun(suite, testCaseDtos);
                })
            .toList();

    return new TestTypeResults(
        type.getId(),
        type.getName(),
        suiteDtos,
        run.getTestProcessingStatus() == WorkflowRun.TestProcessingStatus.PROCESSING,
        new TestResultsDto.TestTypeStats(
            (int) suiteRuns.getTotalElements(),
            summary.getTests(),
            summary.getTests()
                - (summary.getFailures() + summary.getErrors() + summary.getSkipped()),
            summary.getFailures(),
            summary.getErrors(),
            summary.getSkipped(),
            summary.getTime(),
            summary.getHasUpdates() ? 1 : 0));
  }
}
