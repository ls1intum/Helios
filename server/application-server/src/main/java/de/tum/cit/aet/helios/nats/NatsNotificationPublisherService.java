package de.tum.cit.aet.helios.nats;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.NatsMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class NatsNotificationPublisherService {

    private static final Logger logger = LoggerFactory.getLogger(NatsNotificationPublisherService.class);

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
            logger.info("NATS is disabled. Skipping initialization.");
            return;
        }

        validateConfigurations();
        Options options = buildNatsOptions();

        while (true) {
            try {
                natsConnection = Nats.connect(options);
                jetStream = natsConnection.jetStream();
                logger.info("Connected to NATS at {}", natsServer);
                // publishDummyNotification();
                return;
            } catch (IOException | InterruptedException e) {
                logger.error("NATS connection error: {}", e.getMessage(), e);
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
                .connectionListener((conn, type) -> logger.info("Connection event - Server: {}, {}",
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
        logger.info("Published message to subject '{}'", "notification." + subject);
    }

    private void publishDummyNotification() {
        String dummyMessage = "This is a dummy notification message.";
        try {
            publishNotification("dummy", dummyMessage.getBytes(StandardCharsets.UTF_8));
            logger.info("Dummy notification message published successfully.");
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to publish dummy notification message: {}", e.getMessage(), e);
        }
    }
}
