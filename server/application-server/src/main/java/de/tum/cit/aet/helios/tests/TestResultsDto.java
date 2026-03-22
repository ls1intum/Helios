package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.tests.TestCaseRun.TestStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

/**
 * DTO for grouped test results. Test suites are grouped by workflow, enabling separate display of
 * different test types.
 */
public record TestResultsDto(@NonNull List<TestTypeResults> testResults, boolean isProcessing) {

  public record TestTypeResults(
      @NonNull Long testTypeId,
      @NonNull String testTypeName,
      @NonNull List<TestSuiteDto> testSuites,
      @NonNull Boolean isProcessing,
      @NonNull TestTypeStats stats) {}

  public record TestTypeStats(
      @NonNull Integer totalSuites,
      @NonNull Integer totalTests,
      @NonNull Integer passed,
      @NonNull Integer failures,
      @NonNull Integer errors,
      @NonNull Integer skipped,
      @NonNull Double totalTime,
      @NonNull Integer totalUpdates) {}

  record TestSuiteDto(
      @NonNull Long id,
      @NonNull String name,
      @NonNull LocalDateTime timestamp,
      @NonNull Integer tests,
      @NonNull Integer failures,
      @NonNull Integer errors,
      @NonNull Integer skipped,
      @NonNull Double time,
      String systemOut,
      @NonNull List<TestCaseDto> testCases) {
    public static TestSuiteDto fromTestSuiteRun(
        TestSuiteRun testSuiteRun, List<TestCaseDto> testCases) {
      return new TestSuiteDto(
          testSuiteRun.getId(),
          testSuiteRun.getName(),
          testSuiteRun.getTimestamp(),
          testSuiteRun.getTests(),
          testSuiteRun.getFailures(),
          testSuiteRun.getErrors(),
          testSuiteRun.getSkipped(),
          testSuiteRun.getTime(),
          testSuiteRun.getSystemOut(),
          testCases);
    }
  }

  record TestCaseDto(
      @NonNull Long id,
      @NonNull String name,
      @NonNull String className,
      @NonNull TestStatus status,
      TestStatus previousStatus,
      @NonNull Double time,
      String message,
      String stackTrace,
      String systemOut,
      String errorType,
      Double flakinessScore,
      Double defaultBranchFailureRate,
      Double combinedFailureRate,
      Boolean failsInDefaultBranch) {
    public static TestCaseDto fromTestCase(TestCaseRun testCaseRun) {
      TestCase testCase = testCaseRun.getTestCase();
      return new TestCaseDto(
          testCaseRun.getId(),
          testCase.getName(),
          testCase.getClassName(),
          testCaseRun.getStatus(),
          testCaseRun.getPreviousStatus(),
          testCaseRun.getTime(),
          testCaseRun.getMessage(),
          testCaseRun.getStackTrace(),
          testCaseRun.getSystemOut(),
          testCaseRun.getErrorType(),
          testCase.getFlakinessScore(),
          testCase.getDefaultBranchFailureRate(),
          testCase.getCombinedFailureRate(),
          testCaseRun.isFailsInDefaultBranch());
    }
  }
}
