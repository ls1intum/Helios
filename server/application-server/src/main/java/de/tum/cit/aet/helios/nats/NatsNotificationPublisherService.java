package de.tum.cit.aet.helios.nats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.notification.NotificationConfig;
import de.tum.cit.aet.helios.notification.email.EmailNotificationPayload;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.util.NotificationEligibilityService;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.NatsMessage;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class NatsNotificationPublisherService {

  private Connection natsConnection;

  @Value("${nats.enabled}")
  private boolean isNatsEnabled;

  @Value("${nats.server}")
  private String natsServer;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

  private final ObjectMapper objectMapper;

  private final NotificationConfig notificationConfig;

  private final NotificationEligibilityService notificationEligibilityService;

  private static final String SUBJECT = "notification.message.email";

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (!isNatsEnabled) {
      log.info("NATS is disabled. Skipping initialization.");
      return;
    }

    validateConfigurations();
    Options options = buildNatsOptions();

    while (true) {
      try {
        natsConnection = Nats.connect(options);
        log.info("Connected to NATS at {}", natsServer);
        return;
      } catch (IOException | InterruptedException e) {
        log.error("NATS connection error: {}", e.getMessage(), e);
      }
    }
  }

  private void validateConfigurations() {
    if (natsServer == null || natsServer.trim().isEmpty()) {
      throw new IllegalArgumentException("NATS server configuration is missing.");
    }
  }

  private Options buildNatsOptions() {
    return Options.builder()
        .server(natsServer)
        .token(natsAuthToken.toCharArray())
        .connectionListener(
            (conn, type) ->
                log.info("Connection event - Server: {}, {}", conn.getServerInfo().getPort(), type))
        .maxReconnects(-1)
        .reconnectWait(Duration.ofSeconds(2))
        .build();
  }

  /**
   * Sends an e‑mail notification.
   *
   * @param user user to send the notification to
   * @param dto concrete payload record
   */
  public void send(User user, EmailNotificationPayload dto) {
    log.info("Sending e‑mail notification to user '{}' to email '{}'", user.getLogin(),
        user.getNotificationEmail());
    try {
      // eligibility gate
      if (!notificationEligibilityService.canNotify(user, dto.type())) {
        log.debug("Suppressed {} mail to {}", dto.type(), user.getLogin());
        return;
      }

      final String to = user.getNotificationEmail();

      if (!notificationConfig.isEnabled()) {
        log.info("Notification is disabled. Skipping notification.");
        return;
      }
      if (!isNatsEnabled) {
        log.info("NATS is disabled. Skipping notification.");
        return;
      }
      if (natsConnection == null) {
        log.error("NATS connection is not established. Cannot publish notification!");
        return;
      }

      if (!StringUtils.isNotBlank(to)) {
        log.info(
            "Recipient e‑mail address is missing. "
                + "Cannot publish notification! Email type: {}",
            dto.template());
        return;
      }

      // DTO → Map  (username, environment, repositoryId, …)
      Map<String, Object> parameters =
          objectMapper.convertValue(dto, new TypeReference<>() {
          });


      Map<String, Object> body = new HashMap<>();
      body.put("parameters", parameters);
      body.put("template", dto.template());
      body.put("subject", dto.subject());
      body.put("to", to);
      body.put("timestamp", System.currentTimeMillis());

      byte[] data = objectMapper.writeValueAsBytes(body);

      natsConnection.publish(NatsMessage.builder()
          .subject(SUBJECT)
          .data(data)
          .build());
      log.info("Published notification to '{}': {}", SUBJECT, body);

    } catch (Exception e) {
      log.error("Failed to publish notification: {}", e.getMessage(), e);
    }
  }
}
