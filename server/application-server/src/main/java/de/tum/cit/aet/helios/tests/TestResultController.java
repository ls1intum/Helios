package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsFilterType;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsPageRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestResultController {
  private final TestResultService testResultService;
  private final TestCaseStatisticsService testCaseStatisticsService;

  /**
   * Get the latest test results for a pull request, grouped by workflow. This enables separate
   * display of different test types (Java, E2E, etc.).
   *
   * @param pullRequestId the pull request ID
   * @return the grouped test results
   */
  @GetMapping("/pr/{pullRequestId}")
  public ResponseEntity<TestResultsDto> getLatestTestResultsByPullRequestId(
      @PathVariable Long pullRequestId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "false") boolean onlyFailed) {
    return ResponseEntity.ok(
        testResultService.getLatestTestResultsForPr(
            pullRequestId,
            new TestResultService.TestSearchCriteria(page, size, search, onlyFailed)));
  }

  /**
   * Get the latest test results for a branch, grouped by workflow. This enables separate display of
   * different test types (Java, E2E, etc.).
   *
   * @param branch the branch name
   * @return the grouped test results
   */
  @GetMapping("/branch")
  public ResponseEntity<TestResultsDto> getLatestTestResultsByBranch(
      @RequestParam String branch,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "false") boolean onlyFailed) {
    return ResponseEntity.ok(
        testResultService.getLatestTestResultsForBranch(
            branch, new TestResultService.TestSearchCriteria(page, size, search, onlyFailed)));
  }

  /**
   * Get the test results for a specific workflow run.
   *
   * @param workflowRunId the workflow run ID
   * @return the grouped test results
   */
  @GetMapping("/run/{workflowRunId}")
  public ResponseEntity<TestResultsDto> getTestResultsByWorkflowRunId(
      @PathVariable Long workflowRunId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "false") boolean onlyFailed) {
    return ResponseEntity.ok(
        testResultService.getTestResultsForWorkflowRun(
            workflowRunId,
            new TestResultService.TestSearchCriteria(page, size, search, onlyFailed)));
  }

  /**
   * Look up historical flakiness scores for a list of test cases.
   * Authenticated via repository shared secret.
   * Designed for CI pipelines (e.g. GitHub Actions)
   * that need to annotate failed tests with flakiness data.
   *
   * @param repo the repository settings, populated by {@code RepoSecretFilter}
   * @param request the list of test case identifiers to look up
   * @return matching flakiness scores
   */
  @PostMapping("/flakiness-scores")
  public ResponseEntity<List<TestFlakinessScoreDto>> getFlakinessScores(
      @RequestAttribute("repository") GitRepoSettings repo,
      @Valid @RequestBody TestFlakinessScoreRequest request) {
    Long repositoryId = repo.getRepository().getRepositoryId();
    return ResponseEntity.ok(
        testCaseStatisticsService.getFlakinessScoresForTests(repositoryId, request.testCases()));
  }

  /**
   * Get a paginated, optionally filtered overview of flaky tests for the current repository.
   *
   * <p>The summary section always reflects global (unfiltered) counts. The {@code flakyTests}
   * array contains only the requested page. Use {@code filteredCount} as the paginator total.
   *
   * @param page one-based page index (default 1, as used by the frontend PaginatedTableService)
   * @param size page size (default 20)
   * @param sortDirection optional sort direction ("asc" or "desc") by flakiness score
   * @return aggregated flaky test overview
   */
  @GetMapping("/flaky")
  public ResponseEntity<FlakyTestOverviewDto> getFlakyTestsOverview(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sortDirection,
      @RequestParam(required = false) FlakyTestsFilterType filterType,
      @RequestParam(required = false) String searchTerm) {
    Long repositoryId = RepositoryContext.getRepositoryId();
    FlakyTestsPageRequest pageRequest = FlakyTestsPageRequest.builder()
        .page(page)
        .size(size)
        .sortDirection(sortDirection)
        .filterType(filterType != null ? filterType : FlakyTestsFilterType.ALL)
        .searchTerm(searchTerm)
        .build();
    return ResponseEntity.ok(
        testCaseStatisticsService.getFlakyTestsOverview(repositoryId, pageRequest));
  }

}
