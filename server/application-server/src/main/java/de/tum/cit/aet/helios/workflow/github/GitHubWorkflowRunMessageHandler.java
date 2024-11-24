package de.tum.cit.aet.helios.workflow.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class GitHubWorkflowRunMessageHandler extends GitHubMessageHandler<GHEventPayload.WorkflowRun> {
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubWorkflowSyncService workflowSyncService;

    private GitHubWorkflowRunMessageHandler(
            GitHubWorkflowSyncService workflowSyncService,
            GitHubRepositorySyncService repositorySyncService,
            GitHubPullRequestSyncService pullRequestSyncService) {
        super(GHEventPayload.WorkflowRun.class);

        this.workflowSyncService = workflowSyncService;
        this.repositorySyncService = repositorySyncService;
        this.pullRequestSyncService = pullRequestSyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.WorkflowRun eventPayload) {
        var action = eventPayload.getAction();
        var repository = eventPayload.getRepository();
        var run = eventPayload.getWorkflowRun();

        log.info("Received worfklow run event for repository: {}, workflow run: {}, action: {}",
                repository.getFullName(),
                run.getUrl(),
                action);

        repositorySyncService.processRepository(eventPayload.getRepository());

        try {
            run.getPullRequests().forEach(pullRequest -> {
                pullRequestSyncService.processPullRequest(pullRequest);
            });
        } catch (Exception e) {
            log.error("Failed to process pull requests for workflow run {}: {}", run.getUrl(), e.getMessage());
        }

        workflowSyncService.processRun(run);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.WORKFLOW_RUN;
    }
}
