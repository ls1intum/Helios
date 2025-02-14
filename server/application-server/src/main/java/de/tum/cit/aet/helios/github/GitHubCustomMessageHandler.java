package de.tum.cit.aet.helios.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;

/**
 * Interface for custom message handlers for GitHub events.
 * org.kohsuke.github.GHEventPayload does not support all GitHub events,
 * so this interface allows for custom handling of events.
 */
@Log4j2
public abstract class GitHubCustomMessageHandler<T> implements MessageHandler {

  private final Class<T> payloadType;

  private final ObjectMapper objectMapper;


  protected GitHubCustomMessageHandler(
      Class<T> payloadType,
      ObjectMapper objectMapper) {
    this.payloadType = payloadType;
    this.objectMapper = objectMapper;
  }


  /**
   * Processes a NATS message for this event type.
   *
   * <p>Implementations typically:
   * <ol>
   *   <li>Read the raw JSON from the NATS Message</li>
   *   <li>Deserialize it into an instance of type T</li>
   *   <li>Perform business logic based on the deserialized payload</li>
   * </ol>
   *
   * @param msg the NATS message to process
   */
  @Override
  public void onMessage(Message msg) {
    String eventType = getEventType().toLowerCase();
    String subject = msg.getSubject();
    if (!subject.endsWith(eventType)) {
      log.error(
          "Received message on unexpected subject: {}, expected to end with {}",
          subject,
          eventType);
      return;
    }

    String payload = new String(msg.getData(), StandardCharsets.UTF_8);

    try {
      T eventPayload = parseEventPayload(payload);
      handleEvent(eventPayload);
    } catch (Exception e) {
      log.error(
          "Unexpected error while handling message for subject {}: {}", subject, e.getMessage(), e);
    }
  }


  private T parseEventPayload(String payload) {
    try {
      log.info("Parsing the payload to type: {}", payloadType);
      return objectMapper.readValue(payload, payloadType);
    } catch (IOException e) {
      log.error("Failed to parse payload", e);
    } catch (Exception e) {
      log.error("Failed to process payload", e);
    }
    return null;
  }

  /**
   * Returns the event name or “key” that this handler handles.
   * e.g. "deployment_protection_rule" or "some_other_event"
   */
  protected abstract String getEventType();


  protected abstract void handleEvent(T eventPayload);

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
}