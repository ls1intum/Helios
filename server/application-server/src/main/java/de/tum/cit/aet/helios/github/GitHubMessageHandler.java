package de.tum.cit.aet.helios.github;

import io.nats.client.Message;
import io.nats.client.MessageHandler;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public abstract class GitHubMessageHandler<T extends GHEventPayload> implements MessageHandler {
  private final Class<T> payloadType;

  protected GitHubMessageHandler(Class<T> payloadType) {
    this.payloadType = payloadType;
  }

  @Override
  public void onMessage(Message msg) {
    String eventType = getHandlerEvent().name().toLowerCase();
    String subject = msg.getSubject();
    if (!subject.endsWith(eventType)) {
      log.error(
          "Received message on unexpected subject: {}, expected to end with {}",
          subject,
          eventType);
      return;
    }

    String payload = new String(msg.getData(), StandardCharsets.UTF_8);

    try (StringReader reader = new StringReader(payload)) {
      T eventPayload = GitHub.offline().parseEventPayload(reader, payloadType);
      handleEvent(eventPayload);
    } catch (IOException e) {
      log.error("Failed to parse payload for subject {}: {}", subject, e.getMessage(), e);
    } catch (Exception e) {
      log.error(
          "Unexpected error while handling message for subject {}: {}", subject, e.getMessage(), e);
    }
  }

  /**
   * Determines whether this event applies globally across
   * all repositories or is specific to individual repositories.
   *
   * <p>By default, this method returns {@code false},
   * meaning the event is associated with a specific repository
   * and will be monitored separately for each repository configured in the system.
   *
   * <p>If overridden to return {@code true},
   * the event is considered global,
   * meaning it is not tied to any particular
   * repository and will be handled as a single event across all repositories.
   *
   * <p>The NATS consumer uses this method to determine the appropriate subjects to subscribe to.
   * For example, events like {@code installation_repositories}
   * should be marked as global since they apply to all repositories
   * within an installation rather than a single repository.
   *
   * @return {@code true} if the event is global and
   *      applies to all repositories; {@code false} if it is repository-specific.
   */
  public boolean isGlobalEvent() {
    return false;
  }

  /**
   * Handles the parsed event payload.
   *
   * @param eventPayload The parsed GHEventPayload.
   */
  protected abstract void handleEvent(T eventPayload);

  /**
   * Returns the GHEvent that this handler is responsible for.
   *
   * @return The GHEvent.
   */
  protected abstract GHEvent getHandlerEvent();
}
