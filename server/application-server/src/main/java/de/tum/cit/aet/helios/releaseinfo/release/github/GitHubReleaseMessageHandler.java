package de.tum.cit.aet.helios.releaseinfo.release.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.releaseinfo.release.ReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHEventPayload.Release;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class GitHubReleaseMessageHandler extends GitHubMessageHandler<GHEventPayload.Release> {

  private final GitHubReleaseSyncService releaseSyncService;
  private final GitHubRepositorySyncService repositorySyncService;
  private final ReleaseRepository releaseRepository;

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.Release eventPayload) {
    log.info(
        "Received release event for repository: {}, release: {}, action: {}",
        eventPayload.getRepository().getFullName(),
        eventPayload.getRelease().getName(),
        eventPayload.getAction());
    if (eventPayload.getAction().equals("created")
        || eventPayload.getAction().equals("edited")
        || eventPayload.getAction().equals("published")) {
      if (eventPayload.getRelease().isDraft()) {
        return;
      }
      repositorySyncService.processRepository(eventPayload.getRepository());
      releaseSyncService.processRelease(eventPayload.getRelease());
    } else if (eventPayload.getAction().equals("deleted")) {
      releaseRepository.deleteById(eventPayload.getRelease().getId());
    }
  }

  @Override
  protected Class<Release> getPayloadClass() {
    return GHEventPayload.Release.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.RELEASE;
  }
}
