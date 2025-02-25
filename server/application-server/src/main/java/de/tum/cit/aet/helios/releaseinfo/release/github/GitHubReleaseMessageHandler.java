package de.tum.cit.aet.helios.releaseinfo.release.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubReleaseMessageHandler extends GitHubMessageHandler<GHEventPayload.Release> {

  private final GitHubReleaseSyncService releaseSyncService;
  private final GitHubRepositorySyncService repositorySyncService;

  private GitHubReleaseMessageHandler(
      GitHubReleaseSyncService releaseSyncService,
      GitHubRepositorySyncService repositorySyncService) {
    super(GHEventPayload.Release.class);
    this.releaseSyncService = releaseSyncService;
    this.repositorySyncService = repositorySyncService;
  }

  @Override
  protected void handleEvent(GHEventPayload.Release eventPayload) {
    if (eventPayload.getAction().equals("created")) {
      return;
    }
    log.info(
        "Received release event for repository: {}, release: {}, action: {}",
        eventPayload.getRepository().getFullName(),
        eventPayload.getRelease().getName(),
        eventPayload.getAction());
    repositorySyncService.processRepository(eventPayload.getRepository());
    // We don't need to handle the deleted action here, as pull requests are not deleted
    releaseSyncService.processRelease(eventPayload.getRelease());
  }

  @Override
  protected GHEvent getHandlerEvent() {
    return GHEvent.RELEASE;
  }
}
