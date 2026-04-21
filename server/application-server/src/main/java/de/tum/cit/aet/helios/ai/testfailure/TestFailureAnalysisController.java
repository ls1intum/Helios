package de.tum.cit.aet.helios.ai.testfailure;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastWritePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/test-cases")
@RequiredArgsConstructor
public class TestFailureAnalysisController {

  private final TestFailureAnalysisService analysisService;

  @EnforceAtLeastWritePermission
  @PostMapping("/{testCaseId}/failure-analysis")
  public ResponseEntity<TestFailureAnalysisResponseDto> analyzeFailedTest(
      @PathVariable Long repositoryId, @PathVariable Long testCaseId) {
    return ResponseEntity.ok(analysisService.analyzeTestFailure(repositoryId, testCaseId));
  }
}
