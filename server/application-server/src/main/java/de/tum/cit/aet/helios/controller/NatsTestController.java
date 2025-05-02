package de.tum.cit.aet.helios.controller;

import de.tum.cit.aet.helios.nats.NatsNotificationPublisherService;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for testing NATS notifications. This is used for testing purposes only to verify NATS
 * integration.
 */
@RestController
@RequestMapping("/api/test/nats")
@Log4j2
public class NatsTestController {

  private final NatsNotificationPublisherService publisherService;

  @Autowired
  public NatsTestController(NatsNotificationPublisherService publisherService) {
    this.publisherService = publisherService;
  }

  /**
   * Endpoint to send a test notification through NATS.
   *
   * @param subject The subject to publish to (without the "notification." prefix)
   * @param payload Optional JSON payload as a Map, if not provided a default test message is used
   * @return Response indicating success or failure
   */
  @PostMapping("/send")
  public ResponseEntity<Map<String, Object>> sendTestNotification(
      @RequestParam String subject, @RequestBody(required = false) Map<String, Object> payload) {

    log.info("Sending test notification to subject: notification.{}", subject);

    Map<String, Object> response = new HashMap<>();

    try {
      // If no payload is provided, create a test message
      if (payload == null) {
        payload = new HashMap<>();
        payload.put("message", "Test message from application server");
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("test", true);
      }

      // Add notification. prefix to subject if not present
      String fullSubject =
          subject.startsWith("notification.message.") ? subject : "notification.message." + subject;

      // Convert map to JSON string bytes
      byte[] jsonData =
          new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(payload);

      // Publish the notification
      publisherService.publishNotification(fullSubject, jsonData);

      response.put("success", true);
      response.put("message", "Test notification sent successfully to " + fullSubject);
      response.put("payload", payload);

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error sending test notification: {}", e.getMessage(), e);

      response.put("success", false);
      response.put("error", e.getMessage());

      return ResponseEntity.badRequest().body(response);
    }
  }
}
