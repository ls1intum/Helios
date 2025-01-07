package de.tum.cit.aet.helios.label.github;


import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.label.LabelRepository;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubLabelMessageHandler extends GitHubMessageHandler<GHEventPayload.Label> {

  private static final Logger logger = LoggerFactory.getLogger(GitHubLabelMessageHandler.class);

  private final LabelRepository labelRepository;
  private final GitHubLabelSyncService labelSyncService;
  private final GitHubRepositorySyncService repositorySyncService;

  private GitHubLabelMessageHandler(
      LabelRepository labelRepository,
      GitHubLabelSyncService labelSyncService,
      GitHubRepositorySyncService repositorySyncService
  ) {
    super(GHEventPayload.Label.class);
    this.labelRepository = labelRepository;
    this.labelSyncService = labelSyncService;
    this.repositorySyncService = repositorySyncService;
  }

  @Override
  protected void handleEvent(GHEventPayload.Label eventPayload) {
    var action = eventPayload.getAction();
    var repository = eventPayload.getRepository();
    var label = eventPayload.getLabel();
    logger.info(
        "Received label event for repository: {}, action: {}, labelId: {}",
        repository.getFullName(),
        action,
        label.getId()
    );

    repositorySyncService.processRepository(repository);

    if (action.equals("deleted")) {
      labelRepository.deleteById(label.getId());
    } else {
      labelSyncService.processLabel(label);
    }
  }

  @Override
  protected GHEvent getHandlerEvent() {
    return GHEvent.LABEL;
  }
}