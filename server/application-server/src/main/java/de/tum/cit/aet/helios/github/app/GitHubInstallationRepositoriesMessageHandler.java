package de.tum.cit.aet.helios.github.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.github.GitHubClientManager;
import de.tum.cit.aet.helios.github.GitHubCustomMessageHandler;
import de.tum.cit.aet.helios.github.sync.DataSyncStatusService;
import de.tum.cit.aet.helios.github.sync.GitHubDataSyncService;
import de.tum.cit.aet.helios.gitrepo.RepositoryService;
import de.tum.cit.aet.helios.nats.NatsConsumerService;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubInstallationRepositoriesMessageHandler
    extends GitHubCustomMessageHandler<GitHubInstallationPayload> {

  private final GitHubClientManager clientManager;
  private final RepositoryService repositoryService;
  private final GitHubDataSyncService gitHubDataSyncService;
  private final DataSyncStatusService dataSyncStatusService;
  private final NatsConsumerService natsConsumerService;
  private final GitHubClientManager gitHubClientManager;

  @Autowired
  private GitHubInstallationRepositoriesMessageHandler(
      ObjectMapper objectMapper,
      GitHubClientManager clientManager,
      RepositoryService repositoryService,
      GitHubDataSyncService gitHubDataSyncService,
      DataSyncStatusService dataSyncStatusService,
      @Lazy NatsConsumerService natsConsumerService, GitHubClientManager gitHubClientManager) {
    super(GitHubInstallationPayload.class, objectMapper);
    this.clientManager = clientManager;
    this.repositoryService = repositoryService;
    this.gitHubDataSyncService = gitHubDataSyncService;
    this.dataSyncStatusService = dataSyncStatusService;
    this.natsConsumerService = natsConsumerService;
    this.gitHubClientManager = gitHubClientManager;
  }


  @Override
  protected void handleEvent(GitHubInstallationPayload eventPayload) {
    // Check if the GitHub App is configured.
    if (!GitHubClientManager.AuthType.APP.equals(clientManager.getAuthType())) {
      log.warn("Received installation_repositories event, but no GitHub App is configured.");
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

    // Sync repositories added to the GitHub App
    repositoriesAdded.forEach(
        repository -> {
          log.info("[Installation Event Handler] Sync will start for repository: {}",
              repository.getFullName());
          gitHubDataSyncService.syncRepositoryData(repository.getFullName());
          log.info("[Installation Event Handler] Sync completed for repository: {}",
              repository.getFullName());
        });

    // Remove repositories that were uninstalled from the GitHub App
    // This will delete all related data from the database
    // The foreign key relationships are configured with DELETE CASCADE
    // Therefore, removing the repository row will remove all associated data
    repositoriesRemoved.forEach(
        repository -> {
          log.info("[Installation Event Handler] Deletion will start for repository: {}",
              repository.getFullName());
          repositoryService.deleteRepository(repository.getFullName());
          dataSyncStatusService.deleteByRepositoryNameWithOwner(repository.getFullName());
          log.info("[Installation Event Handler] Deletion completed for repository: {}",
              repository.getFullName());
        });

    // Force refresh the GitHub client to ensure the latest installation repositories are exist
    gitHubClientManager.forceRefreshClient();
    // Update NATS consumer subjects
    natsConsumerService.reinitializeConsumer();
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
