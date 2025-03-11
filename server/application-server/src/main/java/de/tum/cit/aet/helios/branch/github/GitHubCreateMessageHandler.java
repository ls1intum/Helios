package de.tum.cit.aet.helios.branch.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class GitHubCreateMessageHandler extends GitHubMessageHandler<GHEventPayload.Create> {
  private final GitHubBranchSyncService branchSyncService;
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubService gitHubService;

  protected Class<GHEventPayload.Create> getPayloadClass() {
    return GHEventPayload.Create.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.CREATE;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.Create eventPayload) {
    String refType = eventPayload.getRefType();
    String ref = eventPayload.getRef();
    GHRepository repository;
    GHBranch branch;
    if ("branch".equals(refType)) {
      log.info(
          "Received branch event for repository: {}, ref: {}, refType: {}, masterBranch {},"
              + " description: {} ",
          eventPayload.getRepository().getFullName(),
          eventPayload.getRef(),
          eventPayload.getRefType(),
          eventPayload.getMasterBranch(),
          eventPayload.getDescription());

      try {
        repository = eventPayload.getRepository();
        var curRepo = gitHubService.getRepository(repository.getFullName());
        branch = curRepo.getBranch(ref);

        repositorySyncService.processRepository(eventPayload.getRepository());
        branchSyncService.processBranch(branch);
      } catch (IOException e) {
        e.printStackTrace();
      }
      return;
    }
  }
}
