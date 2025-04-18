package de.tum.cit.aet.helios.nats;

import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.MessageConsumer;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.sentry.Sentry;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Order(value = 1)
@Service
@Log4j2
public class NatsConsumerService {
  private static final int INITIAL_RECONNECT_DELAY_SECONDS = 2;

  @Value("${nats.enabled}")
  private boolean isNatsEnabled;

  @Value("${nats.timeframe}")
  private int timeframe;

  @Value("${nats.server}")
  private String natsServer;

  @Value("${nats.durableConsumerName}")
  private String durableConsumerName;

  @Value("${nats.consumerInactiveThresholdMinutes}")
  private int consumerInactiveThresholdMinutes;

  @Value("${nats.consumerAckWaitSeconds}")
  private int consumerAckWaitSeconds;

  @Value("${monitoring.repositories}")
  private String[] repositoriesToMonitor;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

  private Connection natsConnection;
  private ConsumerContext consumerContext;

  /** Active NATS message consumer. */
  private MessageConsumer messageConsumer;

  private final Environment environment;

  private final NatsErrorListener natsErrorListener;

  private final NatsMessageHandlerRegistry handlerRegistry;

  public NatsConsumerService(
      Environment environment,
      NatsMessageHandlerRegistry handlerRegistry,
      @Lazy NatsErrorListener natsErrorListener) {
    this.environment = environment;
    this.handlerRegistry = handlerRegistry;
    this.natsErrorListener = natsErrorListener;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (environment.matchesProfiles("openapi")) {
      log.info("OpenAPI profile detected. Skipping NATS initialization.");
      return;
    }

    if (!isNatsEnabled) {
      log.info("NATS is disabled. Skipping NATS initialization.");
      return;
    }

    validateConfigurations();
    Options options = buildNatsOptions();

    while (true) {
      try {
        natsConnection = Nats.connect(options);
        setupOrUpdateConsumer(natsConnection);
        return;
      } catch (IOException | InterruptedException | RuntimeException e) {
        log.error("NATS connection error: {}", e.getMessage(), e);
      }
    }
  }

  private void validateConfigurations() {
    if (natsServer == null || natsServer.trim().isEmpty()) {
      throw new IllegalArgumentException("NATS server configuration is missing.");
    }
    if (repositoriesToMonitor == null || repositoriesToMonitor.length == 0) {
      log.warn("No repositories to monitor are configured.");
    }
  }

  private Options buildNatsOptions() {
    return Options.builder()
        .server(natsServer)
        .token(natsAuthToken.toCharArray())
        .connectionListener(
            (conn, type) ->
                log.info("Connection event - Server: {}, {}", conn.getServerInfo().getPort(), type))
        .errorListener(natsErrorListener)
        .maxReconnects(-1)
        .reconnectWait(Duration.ofSeconds(INITIAL_RECONNECT_DELAY_SECONDS))
        .build();
  }

  private synchronized void setupOrUpdateConsumer(Connection connection)
      throws IOException, InterruptedException, RuntimeException {
    try {
      // Close old consumer if it exists
      if (messageConsumer != null) {
        messageConsumer.close();
        log.info("Closed previous MessageConsumer.");
        messageConsumer = null;
      }

      // Get the stream context for the "github" stream
      StreamContext streamContext = connection.getStreamContext("github");
      // Get the subjects to monitor
      String[] subjects = this.handlerRegistry.getSupportedSubjects().toArray(String[]::new);

      log.info(
          "Setting up consumer for stream 'github' with subjects: {}", Arrays.toString(subjects));

      ConsumerConfiguration.Builder consumerConfigBuilder = null;

      // Check if a consumer with the given durable name already exists
      ConsumerInfo existingConsumer = null;
      if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
        try {
          existingConsumer = streamContext.getConsumerInfo(durableConsumerName);
          log.info("Consumer '{}' found.", durableConsumerName);
        } catch (Exception e) {
          log.info("Consumer '{}' not found; a new one will be created.", durableConsumerName);
        }
      }

      if (existingConsumer != null) {
        // Use existing configuration as base
        ConsumerConfiguration existingConfig = existingConsumer.getConsumerConfiguration();
        log.info("Consumer '{}' already exists. Updating subjects.", durableConsumerName);
        consumerConfigBuilder =
            ConsumerConfiguration.builder(existingConfig)
                .inactiveThreshold(Duration.ofMinutes(consumerInactiveThresholdMinutes))
                .ackWait(Duration.ofSeconds(consumerAckWaitSeconds))
                .filterSubjects(subjects);
      } else {
        log.info("Creating new configuration for consumer.");
        // Create new configuration with deliver policy and start time
        consumerConfigBuilder =
            ConsumerConfiguration.builder()
                .deliverPolicy(DeliverPolicy.ByStartTime)
                .startTime(ZonedDateTime.now().minusDays(timeframe))
                .inactiveThreshold(Duration.ofMinutes(consumerInactiveThresholdMinutes))
                .ackWait(Duration.ofSeconds(consumerAckWaitSeconds))
                .filterSubjects(subjects);

        if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
          consumerConfigBuilder = consumerConfigBuilder.durable(durableConsumerName);
        }
      }

      ConsumerConfiguration consumerConfig = consumerConfigBuilder.build();
      consumerContext = streamContext.createOrUpdateConsumer(consumerConfig);
      log.info(
          "Consumer created or updated with name '{}' and configuration: {}",
          consumerContext.getConsumerInfo().getName(),
          consumerConfig);

      messageConsumer = consumerContext.consume(this::handleMessage);
      log.info("Successfully started consuming messages.");
    } catch (JetStreamApiException e) {
      log.error("JetStream API exception: {}", e.getMessage(), e);
      throw new IOException("Failed to set up consumer.", e);
    } catch (Exception e) {
      log.error("Error setting up consumer: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to set up consumer.", e);
    }
  }

  private void handleMessage(Message msg) {
    try {
      final String subject = msg.getSubject();

      log.info("Received message with subject: {}", subject);

      final NatsMessageHandler<?> handler = handlerRegistry.findHandlerForSubject(subject);

      if (handler == null) {
        log.warn("No handler for subject {}", subject);
        return;
      }

      if (handler.shouldAcknowledgeEarly()) {
        msg.ack();
        handler.onMessage(msg);
      } else {
        // Ack after the handler otherwise
        handler.onMessage(msg);
      }
    } catch (IllegalArgumentException e) {
      log.error("Invalid event type in subject '{}': {}", msg.getSubject(), e.getMessage());
    } catch (Exception e) {
      log.error("Error processing message: {}", e.getMessage(), e);
    } finally {
      msg.ack();
    }
  }

  public synchronized void reinitializeConsumer() {
    log.info("NATS Consumer reinitialization process started.");
    if (natsConnection != null) {
      try {
        log.info("Attempting to reinitialize the NATS consumer...");
        setupOrUpdateConsumer(natsConnection);
        log.info("NATS consumer reinitialized successfully.");
      } catch (Exception e) {
        log.error("Failed to reinitialize the NATS consumer: {}", e.getMessage(), e);
        // Log error to Sentry
        Sentry.captureException(e);
      }
    } else {
      log.warn("NATS connection is null. Can not reinitialize consumer.");
    }
  }
}
