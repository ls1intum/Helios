package de.tum.cit.aet.helios.tests.processor;

import de.tum.cit.aet.helios.common.dto.test.TestSuite;
import de.tum.cit.aet.helios.tests.processor.store.InMemoryTestResultStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test-results")
@RequiredArgsConstructor
public class TestResultController {
  private final InMemoryTestResultStore testResultStore;

  @GetMapping("/{workflowRunId}")
  public ResponseEntity<List<TestSuite>> getTestResult(@PathVariable Long workflowRunId) {
    return testResultStore
        .get("test-result:" + workflowRunId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
