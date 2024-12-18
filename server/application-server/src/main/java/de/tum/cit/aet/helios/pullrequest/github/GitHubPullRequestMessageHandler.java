package de.tum.cit.aet.helios.pullrequest.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubPullRequestMessageHandler
    extends GitHubMessageHandler<GHEventPayload.PullRequest> {

  private final GitHubPullRequestSyncService pullRequestSyncService;
  private final GitHubRepositorySyncService repositorySyncService;

  private GitHubPullRequestMessageHandler(
      GitHubPullRequestSyncService pullRequestSyncService,
      GitHubRepositorySyncService repositorySyncService) {
    super(GHEventPayload.PullRequest.class);
    this.pullRequestSyncService = pullRequestSyncService;
    this.repositorySyncService = repositorySyncService;
  }

  @Override
  protected void handleEvent(GHEventPayload.PullRequest eventPayload) {
    log.info(
        "Received pull request event for repository: {}, pull request: {}, action: {}",
        eventPayload.getRepository().getFullName(),
        eventPayload.getPullRequest().getNumber(),
        eventPayload.getAction());
    repositorySyncService.processRepository(eventPayload.getRepository());
    // We don't need to handle the deleted action here, as pull requests are not deleted
    pullRequestSyncService.processPullRequest(eventPayload.getPullRequest());
  }

  @Override
  protected GHEvent getHandlerEvent() {
    return GHEvent.PULL_REQUEST;
  }
}
