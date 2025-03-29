package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.common.dto.test.ProcessTestResultResponse;
import de.tum.cit.aet.helios.common.dto.test.ProcessingStatus;
import de.tum.cit.aet.helios.common.nats.JacksonMessageHandler;
import de.tum.cit.aet.helios.common.nats.TestSubjects;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class ProcessTestResultResponseHandler
    extends JacksonMessageHandler<ProcessTestResultResponse> {

  private final WorkflowRunRepository workflowRunRepository;

  @Override
  protected Class<ProcessTestResultResponse> getPayloadClass() {
    return ProcessTestResultResponse.class;
  }

  @Override
  protected void handleMessage(ProcessTestResultResponse response) {
    log.debug(
        "Received test result response for workflow run {}", response.workflowRunId().toString());

    final var workflowRun =
        this.workflowRunRepository
            .findById(response.workflowRunId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Workflow run with id "
                            + response.workflowRunId()
                            + " not found in the database."));

    if (response.status() == ProcessingStatus.SUCCESS) {
      log.debug(
          "Successfully processed test results for workflow run {}",
          workflowRun.getName(),
          response.status());

      workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSED);
    } else {
      log.error(
          "Failed to process test results for workflow run {}, error message: {}",
          workflowRun.getName(),
          response.errorMessage());

      workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.FAILED);
    }

    List<TestSuite> testSuites = new ArrayList<>();

    for (var result : response.testSuites()) {
      TestSuite testSuite = new TestSuite();
      testSuite.setWorkflowRun(workflowRun);
      testSuite.setName(result.name());
      testSuite.setTests(result.tests());
      testSuite.setFailures(result.failures());
      testSuite.setErrors(result.errors());
      testSuite.setSkipped(result.skipped());
      testSuite.setTime(result.time());
      testSuite.setTimestamp(result.timestamp());
      testSuite.setTestCases(
          result.testCases().stream()
              .map(
                  tc -> {
                    TestCase testCase = new TestCase();
                    testCase.setTestSuite(testSuite);
                    testCase.setName(tc.name());
                    testCase.setClassName(tc.className());
                    testCase.setTime(tc.time());
                    testCase.setStatus(
                        tc.failed()
                            ? TestCase.TestStatus.FAILED
                            : tc.error()
                                ? TestCase.TestStatus.ERROR
                                : tc.skipped()
                                    ? TestCase.TestStatus.SKIPPED
                                    : TestCase.TestStatus.PASSED);
                    testCase.setMessage(tc.message());
                    testCase.setStackTrace(tc.stackTrace());
                    testCase.setErrorType(tc.errorType());
                    return testCase;
                  })
              .toList());
      testSuites.add(testSuite);
    }

    workflowRun.setTestSuites(testSuites);
    this.workflowRunRepository.save(workflowRun);
  }

  @Override
  public String getSubjectPattern() {
    return TestSubjects.PROCESS_TEST_RESULT_RESPONSE;
  }
}
