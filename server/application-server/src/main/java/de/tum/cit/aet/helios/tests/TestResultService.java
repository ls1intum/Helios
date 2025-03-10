package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestCase.TestStatus;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestResultService {
  private final WorkflowRunRepository workflowRunRepository;
  private final BranchRepository branchRepository;
  private final PullRequestRepository pullRequestRepository;

  private List<TestSuite> getTestSuitesForWorkflowRuns(List<WorkflowRun> runs) {
    return runs.stream().flatMap(run -> run.getTestSuites().stream()).toList();
  }

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
                testSuite ->
                    TestResultsDto.TestSuiteDto.fromTestSuite(testSuite, previousStatusProvider))
            .toList(),
        isProcessing);
  }
}
