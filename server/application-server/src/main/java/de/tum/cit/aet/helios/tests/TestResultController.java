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

  // TODO: blend or create new endpoint for E2E tests
  @GetMapping("/pr/{pullRequestId}")
  public ResponseEntity<TestResultsDto> getLatestTestResultsByPullRequestId(
      @PathVariable Long pullRequestId) {
    return ResponseEntity.ok(testResultService.getLatestTestResultsForPr(pullRequestId));
  }

  @GetMapping("/branch")
  public ResponseEntity<TestResultsDto> getLatestTestResultsByBranch(@RequestParam String branch) {
    return ResponseEntity.ok(testResultService.getLatestTestResultsForBranch(branch));
  }
}
