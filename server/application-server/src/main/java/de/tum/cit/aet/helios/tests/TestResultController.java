package de.tum.cit.aet.helios.tests;

import java.util.List;
import kotlin.NotImplementedError;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test-results")
public class TestResultController {
  @GetMapping("/pr/{pullRequestId}")
  public ResponseEntity<List<TestResult>> getTestResultsByPullRequestIdAndHeadCommit(
      @PathVariable Long pullRequestId) {
    throw new NotImplementedError("Not implemented yet");
  }

  @GetMapping("/branch")
  public ResponseEntity<List<TestResult>> getTestResultsByBranchAndHeadCommit(@RequestParam String branch) {
    throw new NotImplementedError("Not implemented yet");
  }
}
