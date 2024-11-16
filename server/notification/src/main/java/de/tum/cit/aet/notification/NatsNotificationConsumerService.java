package de.tum.cit.aet.notification;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;

import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.api.StorageType;

import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

@Order(value = 1)
@Service
public class NatsNotificationConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(NatsNotificationConsumerService.class);

    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 2;

    @Value("${nats.enabled}")
    private boolean isNatsEnabled;

    @Value("${nats.server}")
    private String natsServer;

    @Value("${nats.durableConsumerName}")
    private String durableConsumerName;

    @Value("${nats.auth.token}")
    private String natsAuthToken;

    private Connection natsConnection;
    private ConsumerContext consumerContext;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {        
        logger.info("Initializing NATS Notification consumer service.");
        if (!isNatsEnabled) {
            logger.info("NATS is disabled. Skipping initialization.");
            return;
        }

        validateConfigurations();
        Options options = buildNatsOptions();

        // while (true) {
            try {
                natsConnection = Nats.connect(options);
                // createStreamIfNotExists();
                setupConsumer(natsConnection);
                return;
            } catch (IOException | InterruptedException e) {
                logger.error("NATS connection error: {}", e.getMessage(), e);
            }
        // }
    }

    private void validateConfigurations() {
        if (natsServer == null || natsServer.trim().isEmpty()) {
            throw new IllegalArgumentException("NATS server configuration is missing.");
        }
    }

    private Options buildNatsOptions() {
        return Options.builder()
                .server(natsServer)
                .token(natsAuthToken)
                .connectionListener((conn, type) -> logger.info("Connection event - Server: {}, {}",
                        conn.getServerInfo().getPort(), type))
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(INITIAL_RECONNECT_DELAY_SECONDS))
                .build();
    }

    private void createStreamIfNotExists() throws IOException, InterruptedException {
        try {
            StreamContext streamContext = natsConnection.getStreamContext("notification");
            StreamInfo streamInfo = streamContext.getStreamInfo();
            logger.info("Stream 'notification' already exists: {}", streamInfo);
        } catch (JetStreamApiException e) {
            if (e.getErrorCode() == 10059) { // Stream not found
                logger.info("Stream 'notification' not found, creating it.");
                StreamConfiguration streamConfig = StreamConfiguration.builder()
                        .name("notification")
                        .subjects("notification.>")
                        .storageType(StorageType.File)
                        .build();
                try {
                    natsConnection.jetStreamManagement().addStream(streamConfig);
                    logger.info("Stream 'notification' created successfully.");
                } catch (JetStreamApiException ex) {
                    throw new IOException("Failed to create stream.", ex);
                }
            } else {
                throw new IOException("Failed to check or create stream.", e);
            }
        }
    }

    private void setupConsumer(Connection connection) throws IOException, InterruptedException {
        try {
            StreamContext streamContext = connection.getStreamContext("notification");

            // Check if consumer already exists
            if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
                try {
                    consumerContext = streamContext.getConsumerContext(durableConsumerName);
                } catch (JetStreamApiException e) {
                    consumerContext = null;
                }
            }

            if (consumerContext == null) {
                logger.info("Setting up consumer for subjects: notification.>");
                ConsumerConfiguration.Builder consumerConfigBuilder = ConsumerConfiguration.builder()
                    .filterSubjects("notification.>")
                    .deliverPolicy(DeliverPolicy.All);

                if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
                    consumerConfigBuilder.durable(durableConsumerName);
                }

                ConsumerConfiguration consumerConfig = consumerConfigBuilder.build();
                consumerContext = streamContext.createOrUpdateConsumer(consumerConfig);
            } else {
                logger.info("Consumer already exists. Skipping consumer setup.");
            }

            MessageHandler handler = this::handleMessage;
            consumerContext.consume(handler);
            logger.info("Successfully started consuming messages.");
        } catch (JetStreamApiException e) {
            logger.error("JetStream API exception: {}", e.getMessage(), e);
            throw new IOException("Failed to set up consumer.", e);
        }
    }

    private void handleMessage(Message msg) {
        try {
            String subject = msg.getSubject();
            logger.info("Received message on subject '{}': {}", subject, new String(msg.getData()));
            // Process the message here
            msg.ack();
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage(), e);
        } finally {
            msg.ack();
        }
    }
}
