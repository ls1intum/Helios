package de.tum.cit.aet.helios.tests;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestResultController {
  private final TestResultService testResultService;

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
        testResultService.getLatestTestResultsForPr(pullRequestId, page, size, search, onlyFailed));
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
        testResultService.getLatestTestResultsForBranch(branch, page, size, search, onlyFailed));
  }
}
