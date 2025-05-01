package de.tum.cit.aet.notification.nats.handlers;

import de.tum.cit.aet.notification.nats.JacksonMessageHandler;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles notification messages received from the application server. This is an example handler
 * using the JacksonMessageHandler to automatically deserialize JSON messages into a map.
 */
@Component
@Slf4j
public class NotificationMessageHandler extends JacksonMessageHandler<Map<String, Object>> {

  @Override
  protected Class<Map<String, Object>> getPayloadClass() {
    // This is a workaround for Java's type erasure
    @SuppressWarnings("unchecked")
    Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
    return mapClass;
  }

  @Override
  protected void handleMessage(Map<String, Object> payload) {
    log.info("Processing notification with payload: {}", payload);

    // You would implement your notification handling logic here
    // For example, sending an email based on the notification details

    String type = payload.containsKey("type") ? (String) payload.get("type") : "unknown";
    log.info("Processed notification of type: {}", type);
  }

  @Override
  public String getSubjectPattern() {
    // This handler will process any message with the subject pattern notification.message.*
    return "notification.message.*";
  }
}
