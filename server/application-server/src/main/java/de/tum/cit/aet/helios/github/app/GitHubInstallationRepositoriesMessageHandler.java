package de.tum.cit.aet.helios.github.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.github.GitHubClientManager;
import de.tum.cit.aet.helios.github.GitHubCustomMessageHandler;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubInstallationRepositoriesMessageHandler
    extends GitHubCustomMessageHandler<GitHubInstallationPayload> {

  private final GitHubClientManager clientManager;

  private final GitHubRepositorySyncService repositorySyncService;

  private final GitHubService gitHubService;

  private GitHubInstallationRepositoriesMessageHandler(
      GitHubClientManager clientManager,
      ObjectMapper objectMapper,
      GitHubRepositorySyncService repositorySyncService,
      GitHubService gitHubService) {
    super(GitHubInstallationPayload.class, objectMapper);
    this.clientManager = clientManager;
    this.repositorySyncService = repositorySyncService;
    this.gitHubService = gitHubService;
  }


  @Override
  protected void handleEvent(GitHubInstallationPayload eventPayload) {
    // Check if the GitHub App is configured.
    if (clientManager.getAuthType().equals(GitHubClientManager.AuthType.PAT)) {
      log.warn("Received installation_repositories event but no GitHub App is configured.");
      return;
    }


    log.info(
        "Received installation_repositories event "
            + "for installation id: {}, "
            + "repositories added: {}, "
            + "repositories removed: {}, "
            + "action: {}",
        eventPayload.getInstallation().getAppId(),
        eventPayload.getRepositoriesAdded(),
        eventPayload.getRepositoriesRemoved(),
        eventPayload.getAction()
    );

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
