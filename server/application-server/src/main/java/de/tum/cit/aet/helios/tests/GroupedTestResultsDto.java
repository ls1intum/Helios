package de.tum.cit.aet.helios.tests;

import java.util.List;
import java.util.Map;
import org.springframework.lang.NonNull;

/**
 * DTO for grouped test results. Test suites are grouped by workflow, enabling separate display of
 * different test types.
 */
public record GroupedTestResultsDto(
    @NonNull Map<String, WorkflowTestResults> testResults, boolean isProcessing) {

  /** Results for a specific workflow. */
  public static record WorkflowTestResults(
      @NonNull Long workflowId,
      @NonNull String workflowName,
      @NonNull List<TestResultsDto.TestSuiteDto> testSuites,
      boolean isProcessing) {}
}
