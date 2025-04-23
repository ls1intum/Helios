package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.tests.TestResultProcessor;
import de.tum.cit.aet.helios.workflow.GitHubWorkflowContext;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class GitHubWorkflowRunMessageHandler
    extends GitHubMessageHandler<GHEventPayload.WorkflowRun> {
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubWorkflowRunSyncService workflowSyncService;
  private final TestResultProcessor testResultProcessor;
  private final GitHubService gitHubService;
  @Qualifier("workflowRunTaskScheduler")
  private final TaskScheduler taskScheduler;

  @Override
  protected Class<GHEventPayload.WorkflowRun> getPayloadClass() {
    return GHEventPayload.WorkflowRun.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.WORKFLOW_RUN;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.WorkflowRun eventPayload) {
    var action = eventPayload.getAction();
    var repository = eventPayload.getRepository();
    var githubRun = eventPayload.getWorkflowRun();
    var githubEvent = githubRun.getEvent();

    log.info(
        "Received workflow run event for repository: {}, workflow run: {}, action: {}, event: {}",
        repository.getFullName(),
        githubRun.getUrl(),
        action,
        githubEvent);

    try {
      eventPayload
          .getWorkflowRun()
          .getPullRequests()
          .forEach(
              pr -> {
                log.info("PR: {}", pr.getUrl());
              });
    } catch (Exception e) {
      log.error("Error: {}", e.getMessage());
    }

    repositorySyncService.processRepository(eventPayload.getRepository());


    // Check if this is a workflow_run event
    // (??) When we check artifacts for each status,
    // then for the completed status artifact list return an empty list
    // When it is fixed, we may want to check the artifacts for each status
    // but, we need to delay the processing events when the event name is workflow_run
    // since in the beginning of the workflow, we are uploading the context file,
    // it may or may not be seen when the workflow_run event is received.
    if ("workflow_run".equalsIgnoreCase(githubEvent.name())) {
      log.info("Received workflow_run event, delaying processing");
      taskScheduler.schedule(
          () -> handleWorkflowRunEvent(eventPayload),
          Instant.now().plusSeconds(10)
      );
      return;
    }

    var run = workflowSyncService.processRun(githubRun);
    processTestResult(run);
  }

  private void handleWorkflowRunEvent(GHEventPayload.WorkflowRun eventPayload) {
    log.info("Processing workflow_run event with workflow run id: {}",
        eventPayload.getWorkflowRun().getId());
    var repository = eventPayload.getRepository();
    var githubRun = eventPayload.getWorkflowRun();

    GitHubWorkflowContext context = null;

    try {
      context =
          gitHubService.extractWorkflowContext(repository.getId(), githubRun.getId());
    } catch (Exception e) {
      log.error("Error while extracting workflow context: {}", e.getMessage());
      return;
    }

    if (context == null) {
      log.warn("No workflow context found for workflow run: {}", githubRun.getId());
      return;
    }

    log.info("Context found with triggering workflow run id: {}, head branch: {}, head sha: {}",
        context.runId(), context.headBranch(), context.headSha());

    var run = workflowSyncService.processRunWithContext(githubRun, context);
    processTestResult(run);
  }


  /**
   * Processes the test result for the given workflow run.
   *
   * @param run The workflow run to process
   */
  private void processTestResult(WorkflowRun run) {
    if (run != null && testResultProcessor.shouldProcess(run)) {
      testResultProcessor.processRun(run);
    }
  }
}
