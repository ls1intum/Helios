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
   * Get the latest test results for a pull request. This endpoint returns both Java and E2E test
   * results combined.
   *
   * @param pullRequestId the pull request ID
   * @return the test results
   */
  @GetMapping("/pr/{pullRequestId}")
  public ResponseEntity<TestResultsDto> getLatestTestResultsByPullRequestId(
      @PathVariable Long pullRequestId) {
    return ResponseEntity.ok(testResultService.getLatestTestResultsForPr(pullRequestId));
  }

  /**
   * Get the latest test results for a branch. This endpoint returns both Java and E2E test results
   * combined.
   *
   * @param branch the branch name
   * @return the test results
   */
  @GetMapping("/branch")
  public ResponseEntity<TestResultsDto> getLatestTestResultsByBranch(@RequestParam String branch) {
    return ResponseEntity.ok(testResultService.getLatestTestResultsForBranch(branch));
  }

  /**
   * Get the latest test results for a pull request, grouped by workflow. This enables separate
   * display of different test types (Java, E2E, etc.).
   *
   * @param pullRequestId the pull request ID
   * @return the grouped test results
   */
  @GetMapping("/grouped/pr/{pullRequestId}")
  public ResponseEntity<GroupedTestResultsDto> getLatestGroupedTestResultsByPullRequestId(
      @PathVariable Long pullRequestId) {
    return ResponseEntity.ok(testResultService.getLatestGroupedTestResultsForPr(pullRequestId));
  }

  /**
   * Get the latest test results for a branch, grouped by workflow. This enables separate display of
   * different test types (Java, E2E, etc.).
   *
   * @param branch the branch name
   * @return the grouped test results
   */
  @GetMapping("/grouped/branch")
  public ResponseEntity<GroupedTestResultsDto> getLatestGroupedTestResultsByBranch(
      @RequestParam String branch) {
    return ResponseEntity.ok(testResultService.getLatestGroupedTestResultsForBranch(branch));
  }
}
