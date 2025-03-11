package de.tum.cit.aet.helios.label.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.label.LabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class GitHubLabelMessageHandler extends GitHubMessageHandler<GHEventPayload.Label> {
  private final LabelRepository labelRepository;
  private final GitHubLabelSyncService labelSyncService;
  private final GitHubRepositorySyncService repositorySyncService;

  @Override
  protected Class<GHEventPayload.Label> getPayloadClass() {
    return GHEventPayload.Label.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.LABEL;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.Label eventPayload) {
    var action = eventPayload.getAction();
    var repository = eventPayload.getRepository();
    var label = eventPayload.getLabel();
    log.info(
        "Received label event for repository: {}, action: {}, labelId: {}",
        repository.getFullName(),
        action,
        label.getId());

    repositorySyncService.processRepository(repository);

    if (action.equals("deleted")) {
      labelRepository.deleteById(label.getId());
    } else {
      labelSyncService.processLabel(label);
    }
  }
}
