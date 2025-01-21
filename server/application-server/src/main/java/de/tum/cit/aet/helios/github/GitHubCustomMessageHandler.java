package de.tum.cit.aet.helios.github;

import io.nats.client.Message;

/**
 * Interface for custom message handlers for GitHub events.
 * org.kohsuke.github.GHEventPayload does not support all GitHub events,
 * so this interface allows for custom handling of events.
 */
public interface GitHubCustomMessageHandler<T> {
  /**
   * Returns the event name or “key” that this handler handles.
   * e.g. "deployment_protection_rule" or "some_other_event"
   */
  String getEventType();

  /**
   * Processes a NATS message for this event type.
   * Implementations typically:
   * 1) Read the raw JSON from the NATS Message
   * 2) Deserialize it into T
   * 3) Perform business logic
   */
  void handleMessage(Message msg);
}