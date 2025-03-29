package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.common.dto.test.ProcessTestResultRequest;
import de.tum.cit.aet.helios.common.nats.NatsPublisher;
import de.tum.cit.aet.helios.common.nats.TestSubjects;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class TestWorkflowOrchestrator {
  private final NatsPublisher natsPublisher;
  private final WorkflowRunRepository workflowRunRepository;

  /**
   * Checks if a workflow run should be processed for test results and initiates processing if
   * needed.
   */
  public void handleWorkflowRun(WorkflowRun workflowRun) {
    if (shouldProcessWorkflowRun(workflowRun)) {
      requestTestProcessing(workflowRun);
    }
  }

  public boolean shouldProcessWorkflowRun(WorkflowRun workflowRun) {
    log.debug(
        "Checking if test results should be processed for workflow run, workflow name {}",
        workflowRun.getName());

    if (workflowRun.getStatus() != WorkflowRun.Status.COMPLETED
        || workflowRun.getWorkflow().getLabel() != Workflow.Label.TEST) {
      return false;
    }

    // If it's older than 2 hours from now, don't process it. That should usually not
    // happen. But in case we are receiving old events from NATS, we should not risk
    // processing a lot of old runs (e.g. when the server was down).
    if (workflowRun.getUpdatedAt().plusHours(2).isBefore(OffsetDateTime.now())) {
      return false;
    }

    return workflowRun.getTestProcessingStatus() == null;
  }

  private void requestTestProcessing(WorkflowRun workflowRun) {
    ProcessTestResultRequest request =
        new ProcessTestResultRequest(
            workflowRun.getRepository().getRepositoryId(), workflowRun.getId());

    natsPublisher.publish(TestSubjects.PROCESS_TEST_RESULT_REQUEST, request);
    log.debug("Requested test processing for workflow run {}", workflowRun.getId());

    workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSING);
    workflowRunRepository.save(workflowRun);
  }
}
