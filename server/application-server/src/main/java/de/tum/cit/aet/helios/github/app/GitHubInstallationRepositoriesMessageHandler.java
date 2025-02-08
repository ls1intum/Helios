package de.tum.cit.aet.helios.github.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.github.GitHubClientManager;
import de.tum.cit.aet.helios.github.GitHubCustomMessageHandler;
import de.tum.cit.aet.helios.github.sync.GitHubDataSyncService;
import de.tum.cit.aet.helios.gitrepo.RepositoryService;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubInstallationRepositoriesMessageHandler
    extends GitHubCustomMessageHandler<GitHubInstallationPayload> {

  private final GitHubClientManager clientManager;

  private final GitHubDataSyncService dataSyncService;

  private final RepositoryService repositoryService;

  private GitHubInstallationRepositoriesMessageHandler(
      ObjectMapper objectMapper,
      GitHubClientManager clientManager,
      GitHubDataSyncService dataSyncService,
      RepositoryService repositoryService) {
    super(GitHubInstallationPayload.class, objectMapper);
    this.clientManager = clientManager;
    this.dataSyncService = dataSyncService;
    this.repositoryService = repositoryService;
  }


  @Override
  protected void handleEvent(GitHubInstallationPayload eventPayload) {
    // Check if the GitHub App is configured.
    if (clientManager.getAuthType().equals(GitHubClientManager.AuthType.PAT)) {
      log.warn("Received installation_repositories event but no GitHub App is configured.");
      return;
    }

    List<GitHubInstallationPayload.Repository> repositoriesAdded =
        eventPayload.getRepositoriesAdded();
    List<GitHubInstallationPayload.Repository> repositoriesRemoved =
        eventPayload.getRepositoriesRemoved();


    log.info(
        "Received installation_repositories event "
            + "for installation id: {}, "
            + "repositories added: {}, "
            + "repositories removed: {}, "
            + "action: {}",
        eventPayload.getInstallation().getAppId(),
        repositoriesAdded,
        repositoriesRemoved,
        eventPayload.getAction()
    );

    // Sync repositories added
    repositoriesAdded.forEach(
        repository -> {
          log.info("[Installation Event Handler] Sync will start for repository: {}",
              repository.getFullName());
          dataSyncService.syncRepositoryData(repository.getFullName());
          log.info("[Installation Event Handler] Sync completed for repository: {}",
              repository.getFullName());
        });

    // Sync repositories removed
    repositoriesRemoved.forEach(
        repository -> {
          log.info("[Installation Event Handler] Deletion will start for repository: {}",
              repository.getFullName());
          repositoryService.deleteRepository(repository.getFullName());
          log.info("[Installation Event Handler] Deletion completed for repository: {}",
              repository.getFullName());
        });
  }

  @Override
  public boolean isGlobalEvent() {
    return true;
  }

  @Override
  protected String getEventType() {
    return "installation_repositories";
  }

}
