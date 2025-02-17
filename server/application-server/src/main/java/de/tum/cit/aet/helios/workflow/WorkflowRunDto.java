package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.tests.TestCase;
import de.tum.cit.aet.helios.tests.TestCase.TestStatus;
import de.tum.cit.aet.helios.tests.TestSuite;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Conclusion;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Status;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public record WorkflowRunDto(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String displayTitle,
    @NonNull Status status,
    @NonNull Long workflowId,
    Conclusion conclusion,
    @NonNull String htmlUrl,
    @NonNull Workflow.Label label,
    WorkflowRun.TestProcessingStatus testProcessingStatus,
    @NonNull List<TestSuiteDto> testSuites) {
  public static WorkflowRunDto fromWorkflowRun(WorkflowRun run, boolean includeTestSuites) {
    return new WorkflowRunDto(
        run.getId(),
        run.getName(),
        run.getDisplayTitle(),
        run.getStatus(),
        run.getWorkflow().getId(),
        run.getConclusion().orElse(null),
        run.getHtmlUrl(),
        run.getWorkflow().getLabel(),
        run.getTestProcessingStatus(),
        !includeTestSuites
            ? List.of()
            : run.getTestSuites().stream().map(TestSuiteDto::fromTestSuite).toList());
  }

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
    public static TestSuiteDto fromTestSuite(TestSuite testSuite) {
      return new TestSuiteDto(
          testSuite.getId(),
          testSuite.getName(),
          testSuite.getTimestamp(),
          testSuite.getTests(),
          testSuite.getFailures(),
          testSuite.getErrors(),
          testSuite.getSkipped(),
          testSuite.getTime(),
          testSuite.getTestCases().stream().map(TestCaseDto::fromTestCase).toList());
    }
  }

  static record TestCaseDto(
      @NonNull Long id,
      @NonNull String name,
      @NonNull String className,
      @NonNull TestStatus status,
      @NonNull Double time,
      String message,
      String stackTrace,
      String errorType) {
    public static TestCaseDto fromTestCase(TestCase testCase) {
      return new TestCaseDto(
          testCase.getId(),
          testCase.getName(),
          testCase.getClassName(),
          testCase.getStatus(),
          testCase.getTime(),
          testCase.getMessage(),
          testCase.getStackTrace(),
          testCase.getErrorType());
    }
  }
}
