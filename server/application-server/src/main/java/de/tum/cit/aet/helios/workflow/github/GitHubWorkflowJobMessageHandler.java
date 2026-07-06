package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.nats.JacksonMessageHandler;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class GitHubWorkflowJobMessageHandler
    extends JacksonMessageHandler<GitHubWorkflowJobPayload> {

  private final GitHubService gitHubService;
  private final GitHubWorkflowJobTimingService gitHubWorkflowJobTimingService;
  private final GitHubWorkflowJobSyncService gitHubWorkflowJobSyncService;

  @Override
  protected Class<GitHubWorkflowJobPayload> getPayloadClass() {
    return GitHubWorkflowJobPayload.class;
  }

  @Override
  public String getSubjectPattern() {
    return "github.*.*.workflow_job";
  }

  @Override
  protected void handleMessage(GitHubWorkflowJobPayload payload) {
    if (payload.repository() == null || payload.repository().fullName() == null) {
      return;
    }

    List<String> repos;
    try {
      repos = gitHubService.getInstalledRepositories();
    } catch (IOException e) {
      log.error("Failed to get installed repositories", e);
      return;
    }

    if (!repos.contains(payload.repository().fullName())) {
      log.warn("Received event for uninstalled repository {}", payload.repository().fullName());
      return;
    }

    gitHubWorkflowJobTimingService.persistDurations(payload);
    gitHubWorkflowJobSyncService.syncFromPayload(payload);
  }
}
