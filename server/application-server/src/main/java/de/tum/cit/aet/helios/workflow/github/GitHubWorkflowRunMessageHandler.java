package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class GitHubWorkflowRunMessageHandler
    extends GitHubMessageHandler<GHEventPayload.WorkflowRun> {
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubWorkflowRunSyncService workflowSyncService;

  private GitHubWorkflowRunMessageHandler(
      GitHubWorkflowRunSyncService workflowSyncService,
      GitHubRepositorySyncService repositorySyncService) {
    super(GHEventPayload.WorkflowRun.class);

    this.workflowSyncService = workflowSyncService;
    this.repositorySyncService = repositorySyncService;
  }

  @Override
  protected void handleEvent(GHEventPayload.WorkflowRun eventPayload) {
    var action = eventPayload.getAction();
    var repository = eventPayload.getRepository();
    var run = eventPayload.getWorkflowRun();

    log.info(
        "Received worfklow run event for repository: {}, workflow run: {}, action: {}",
        repository.getFullName(),
        run.getUrl(),
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
    workflowSyncService.processRun(run);
  }

  @Override
  protected GHEvent getHandlerEvent() {
    return GHEvent.WORKFLOW_RUN;
  }
}
