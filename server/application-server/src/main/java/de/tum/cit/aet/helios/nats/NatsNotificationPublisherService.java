package de.tum.cit.aet.helios.nats;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.NatsMessage;
import lombok.extern.log4j.Log4j2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@Log4j2
public class NatsNotificationPublisherService {

    private Connection natsConnection;
    private JetStream jetStream;

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
                jetStream = natsConnection.jetStream();
                log.info("Connected to NATS at {}", natsServer);
                // publishDummyNotification();
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
                .connectionListener((conn, type) -> log.info("Connection event - Server: {}, {}",
                        conn.getServerInfo().getPort(), type))
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(2))
                .build();
    }

    public void publishNotification(String subject, byte[] message) throws IOException, InterruptedException {
        NatsMessage msg = NatsMessage.builder()
                .subject("notification." + subject)
                .data(message)
                .build();
        natsConnection.publish(msg);
        log.info("Published message to subject '{}'", "notification." + subject);
    }

    private void publishDummyNotification() {
        String dummyMessage = "This is a dummy notification message.";
        try {
            publishNotification("dummy", dummyMessage.getBytes(StandardCharsets.UTF_8));
            log.info("Dummy notification message published successfully.");
        } catch (IOException | InterruptedException e) {
            log.error("Failed to publish dummy notification message: {}", e.getMessage(), e);
        }
    }
}
