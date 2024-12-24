package de.tum.cit.aet.helios.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.NatsMessage;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class NatsNotificationPublisherService {

  private Connection natsConnection;

  @Value("${nats.enabled}")
  private boolean isNatsEnabled;

  @Value("${nats.server}")
  private String natsServer;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

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

  public void publishNotification(String subject, byte[] message)
      throws IOException, InterruptedException {
    NatsMessage msg =
        NatsMessage.builder().subject("notification." + subject).data(message).build();
    natsConnection.publish(msg);
    log.info("Published message to subject '{}'", "notification." + subject);
  }
}
