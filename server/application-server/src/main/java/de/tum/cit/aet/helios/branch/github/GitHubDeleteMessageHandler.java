package de.tum.cit.aet.helios.branch.github;

import de.tum.cit.aet.helios.branch.BranchService;
import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class GitHubDeleteMessageHandler extends GitHubMessageHandler<GHEventPayload.Delete> {
  private final BranchService branchService;

  @Override
  protected Class<GHEventPayload.Delete> getPayloadClass() {
    return GHEventPayload.Delete.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.DELETE;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.Delete eventPayload) {
    String refType = eventPayload.getRefType();
    String ref = eventPayload.getRef();
    GHRepository repository;
    if ("branch".equals(refType)) {
      log.info(
          "Received branch event for repository: {}, ref: {}, refType: {}",
          eventPayload.getRepository().getFullName(),
          eventPayload.getRef(),
          eventPayload.getRefType());

      try {
        repository = eventPayload.getRepository();
        // delete the branch from db
        branchService.deleteBranchByNameAndRepositoryId(ref, repository.getId());
      } catch (Exception e) {
        e.printStackTrace();
      }
      return;
    }
  }
}
