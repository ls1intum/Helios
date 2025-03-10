package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.tests.TestCase.TestStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.lang.NonNull;

public record TestResultsDto(@NonNull List<TestSuiteDto> testSuites, boolean isProcessing) {

  static record TestSuiteDto(
      @NonNull Long id,
      @NonNull String name,
      @NonNull LocalDateTime timestamp,
      @NonNull Integer tests,
      @NonNull Integer failures,
      @NonNull Integer errors,
      @NonNull Integer skipped,
      @NonNull Double time,
      @NonNull List<TestCaseDto> testCases) {
    public static TestSuiteDto fromTestSuite(
        TestSuite testSuite, Function<TestCase, Optional<TestStatus>> previousStatusProvider) {
      return new TestSuiteDto(
          testSuite.getId(),
          testSuite.getName(),
          testSuite.getTimestamp(),
          testSuite.getTests(),
          testSuite.getFailures(),
          testSuite.getErrors(),
          testSuite.getSkipped(),
          testSuite.getTime(),
          testSuite.getTestCases().stream()
              .map(tc -> TestCaseDto.fromTestCase(tc, previousStatusProvider.apply(tc)))
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
      String errorType) {
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
          testCase.getErrorType());
    }
  }
}
