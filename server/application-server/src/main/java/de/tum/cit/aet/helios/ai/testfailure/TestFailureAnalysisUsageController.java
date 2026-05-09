package de.tum.cit.aet.helios.ai.testfailure;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastWritePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test-failure-analysis")
@RequiredArgsConstructor
public class TestFailureAnalysisUsageController {

  private final TestFailureAnalysisService analysisService;

  @EnforceAtLeastWritePermission
  @GetMapping("/usage")
  public ResponseEntity<TestFailureAnalysisUsageDto> getFailureAnalysisUsage() {
    return ResponseEntity.ok(analysisService.getUsage());
  }
}
