package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.tests.TestResultProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class GitHubWorkflowRunMessageHandler
    extends GitHubMessageHandler<GHEventPayload.WorkflowRun> {
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubWorkflowRunSyncService workflowSyncService;
  private final TestResultProcessor testResultProcessor;

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

    log.info(
        "Received worfklow run event for repository: {}, workflow run: {}, action: {}",
        repository.getFullName(),
        githubRun.getUrl(),
        action);

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

    var run = workflowSyncService.processRun(githubRun);

    if (run != null && testResultProcessor.shouldProcess(run)) {
      testResultProcessor.processRun(run);
    }
  }
}
