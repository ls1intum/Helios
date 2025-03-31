package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.tests.TestCase.TestStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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

  /** Results for a specific workflow. */
  public static record WorkflowTestResults(
      @NonNull Long workflowId,
      @NonNull String workflowName,
      @NonNull List<TestResultsDto.TestSuiteDto> testSuites,
      boolean isProcessing) {}

  static record TestSuiteDto(
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
    public static TestSuiteDto fromTestSuite(
        TestSuite testSuite, Function<TestCase, Optional<TestStatus>> previousStatusProvider) {
      return fromTestSuite(
          testSuite, previousStatusProvider, tc -> new TestCaseStatisticsInfo(false, 0.0, false));
    }

    public static TestSuiteDto fromTestSuite(
        TestSuite testSuite,
        Function<TestCase, Optional<TestStatus>> previousStatusProvider,
        Function<TestCase, TestCaseStatisticsInfo> statisticsProvider) {
      return new TestSuiteDto(
          testSuite.getId(),
          testSuite.getName(),
          testSuite.getTimestamp(),
          testSuite.getTests(),
          testSuite.getFailures(),
          testSuite.getErrors(),
          testSuite.getSkipped(),
          testSuite.getTime(),
          testSuite.getSystemOut(),
          testSuite.getTestCases().stream()
              .map(
                  tc -> {
                    TestCaseStatisticsInfo stats = statisticsProvider.apply(tc);
                    tc.setFlaky(stats.isFlaky());
                    tc.setFailureRate(stats.failureRate());
                    tc.setFailsInDefaultBranch(stats.failsInDefaultBranch());
                    return TestCaseDto.fromTestCase(tc, previousStatusProvider.apply(tc));
                  })
              .toList());
    }
  }

  static record TestCaseDto(
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
      Boolean isFlaky,
      Double failureRate,
      Boolean failsInDefaultBranch) {
    public static TestCaseDto fromTestCase(
        TestCase testCase, Optional<TestStatus> previousStatusProvider) {
      return new TestCaseDto(
          testCase.getId(),
          testCase.getName(),
          testCase.getClassName(),
          testCase.getStatus(),
          previousStatusProvider.orElse(null),
          testCase.getTime(),
          testCase.getMessage(),
          testCase.getStackTrace(),
          testCase.getSystemOut(),
          testCase.getErrorType(),
          testCase.isFlaky(),
          testCase.getFailureRate(),
          testCase.isFailsInDefaultBranch());
    }
  }

  /** Record for storing test case statistics information. */
  record TestCaseStatisticsInfo(
      boolean isFlaky, double failureRate, boolean failsInDefaultBranch) {}
}
