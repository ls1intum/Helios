package de.tum.cit.aet.helios.pullrequest.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class GitHubPullRequestMessageHandler
    extends GitHubMessageHandler<GHEventPayload.PullRequest> {

  private final GitHubPullRequestSyncService pullRequestSyncService;
  private final GitHubRepositorySyncService repositorySyncService;

  @Override
  protected Class<GHEventPayload.PullRequest> getPayloadClass() {
    return GHEventPayload.PullRequest.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.PULL_REQUEST;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.PullRequest eventPayload) {
    log.info(
        "Received pull request event for repository: {}, pull request: {}, action: {}",
        eventPayload.getRepository().getFullName(),
        eventPayload.getPullRequest().getNumber(),
        eventPayload.getAction());
    repositorySyncService.processRepository(eventPayload.getRepository());
    // We don't need to handle the deleted action here, as pull requests are not deleted
    pullRequestSyncService.processPullRequest(eventPayload.getPullRequest());
  }
}
