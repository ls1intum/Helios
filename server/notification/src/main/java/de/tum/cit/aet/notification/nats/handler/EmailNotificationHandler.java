package de.tum.cit.aet.notification.nats.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.notification.nats.NatsMessageHandler;
import de.tum.cit.aet.notification.service.EmailService;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Handler for email notifications received via NATS. Processes messages with the subject
 * "notification.message.email".
 */
@Component
@Log4j2
public class EmailNotificationHandler extends NatsMessageHandler<Map<String, Object>> {

  private final EmailService emailService;
  private final ObjectMapper objectMapper;

  /**
   * Constructor for EmailNotificationHandler.
   *
   * @param emailService the email service to use for sending emails
   */
  public EmailNotificationHandler(EmailService emailService) {
    this.emailService = emailService;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public String getSubjectPattern() {
    return "notification.message.email";
  }

  @Override
  protected Map<String, Object> parsePayload(byte[] data) throws Exception {
    return objectMapper.readValue(data, Map.class);
  }

  @Override
  protected void handleMessage(Map<String, Object> payload) {
    log.info("Processing email notification message with payload: {}", payload);

    // check if payload contains timestamp
    if (payload.containsKey("timestamp")) {
      log.info("Timestamp: {}", payload.get("timestamp"));
      // check if timestamp is older than 3 minutes
      if (System.currentTimeMillis() - (Long) payload.get("timestamp") > 3 * 60 * 1000) {
        log.info("Skipping email notification because timestamp is older than 5 minutes");
        return;
      }
    }

    // Check if the message is using the template format
    if (payload.containsKey("template")) {
      processTemplatedEmail(payload);
    } else {
      // Fallback to direct email format
      processDirectEmail(payload);
    }
  }

  /**
   * Process an email using the template-based approach.
   *
   * @param payload the message payload
   */
  private void processTemplatedEmail(Map<String, Object> payload) {
    log.info("Processing templated email notification message.");
    // Extract required fields
    String recipient = (String) payload.get("to");
    String templateName = (String) payload.get("template");
    String subject = (String) payload.get("subject");

    // Extract template parameters, defaulting to empty map if not present
    @SuppressWarnings("unchecked")
    Map<String, Object> parameters =
        payload.containsKey("parameters")
            ? (Map<String, Object>) payload.get("parameters")
            : new HashMap<>();

    // Validate required fields
    if (recipient == null || templateName == null || subject == null) {
      log.error("Missing required fields in templated email notification: {}", payload);
      return;
    }

    log.info(
        "Sending templated email notification to: {} using template: {} with subject: {}",
        recipient,
        templateName,
        subject);

    // Send the email using template
    boolean success = emailService.sendTemplatedEmail(recipient, templateName, parameters, subject);
    if (success) {
      log.info(
          "Successfully processed templated email notification to: {} using template: {}",
          recipient,
          templateName);
    } else {
      log.warn("Failed to process templated email notification to: {}", recipient);
    }
  }

  /**
   * Process an email using the direct content approach (legacy format).
   *
   * @param payload the message payload
   */
  private void processDirectEmail(Map<String, Object> payload) {
    // Extract required fields
    String recipient = (String) payload.get("to");
    String subject = (String) payload.get("subject");
    String body = (String) payload.get("body");

    // Validate required fields
    if (recipient == null || subject == null || body == null) {
      log.error("Missing required fields in direct email notification: {}", payload);
      return;
    }

    // Send the email directly
    boolean success = emailService.sendEmail(recipient, subject, body);

    if (success) {
      log.info("Successfully processed direct email notification to: {}", recipient);
    } else {
      log.warn("Failed to process direct email notification to: {}", recipient);
    }
  }
}
