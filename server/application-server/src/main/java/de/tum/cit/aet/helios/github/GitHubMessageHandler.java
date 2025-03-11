package de.tum.cit.aet.helios.github;

import de.tum.cit.aet.helios.nats.NatsMessageHandler;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public abstract class GitHubMessageHandler<T extends GHEventPayload> extends NatsMessageHandler<T> {
  @Autowired private GitHubService gitHubService;

  protected abstract Class<T> getPayloadClass();

  protected abstract GHEvent getPayloadType();

  @Override
  public String getSubjectPattern() {
    return String.format("github.*.*.%s", this.getPayloadType().name().toLowerCase());
  }

  @Override
  protected T parsePayload(byte[] data) throws Exception {
    try (StringReader reader = new StringReader(new String(data))) {
      return GitHub.offline().parseEventPayload(reader, this.getPayloadClass());
    }
  }

  /**
   * Processes a GitHub webhook event for a repository that is installed
   *
   * @param payload The GitHub event payload for a installed repository. The payload type varies
   *     depending on the event type (Push, Pull Request, etc.).
   */
  protected abstract void handleInstalledRepositoryEvent(T event);

  @Override
  protected void handleMessage(T event) {
    List<String> repos = List.of();

    try {
      repos = gitHubService.getInstalledRepositories();
    } catch (final IOException ex) {
      log.error("Failed to get installed repositories", ex);
      return;
    }

    if (!repos.contains(event.getRepository().getFullName())) {
      log.warn("Received event for uninstalled repository {}", event.getRepository().getFullName());
      return;
    }

    this.handleInstalledRepositoryEvent(event);
  }
}
