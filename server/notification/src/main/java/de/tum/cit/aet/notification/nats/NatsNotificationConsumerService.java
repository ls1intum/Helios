package de.tum.cit.aet.notification.nats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Order(value = 1)
@Service
@Log4j2
public class NatsNotificationConsumerService {

  private static final int INITIAL_RECONNECT_DELAY_SECONDS = 2;
  private static final int MAX_RETRIES = 10;
  private static final int MAX_RECENT_MESSAGES = 20; // Maximum number of recent messages to store

  @Value("${nats.enabled}")
  private boolean isNatsEnabled;

  @Value("${nats.timeframe}")
  private int timeframe;

  @Value("${nats.server}")
  private String natsServer;

  @Value("${nats.durableConsumerName}")
  private String durableConsumerName;

  @Value("${nats.consumerInactiveThresholdMinutes:5}")
  private int consumerInactiveThresholdMinutes;

  @Value("${nats.consumerAckWaitSeconds:30}")
  private int consumerAckWaitSeconds;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

  private Connection natsConnection;
  private ConsumerContext consumerContext;
  private MessageConsumer messageConsumer;
  private String[] currentSubjects = new String[0];

  private NatsErrorListener natsErrorListener;
  private final NatsMessageHandlerRegistry handlerRegistry;

  // For test purposes - store recent messages
  private final LinkedList<Map<String, Object>> recentMessages = new LinkedList<>();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public NatsNotificationConsumerService(NatsMessageHandlerRegistry handlerRegistry) {
    this.handlerRegistry = handlerRegistry;
  }

  @Autowired
  public void setNatsErrorListener(@Lazy NatsErrorListener natsErrorListener) {
    this.natsErrorListener = natsErrorListener;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    log.info("Initializing NATS Notification consumer service.");
    if (!isNatsEnabled) {
      log.info("NATS is disabled. Skipping initialization.");
      return;
    }

    validateConfigurations();
    Options options = buildNatsOptions();

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        natsConnection = Nats.connect(options);
        setupOrUpdateConsumer(natsConnection);
        return;
      } catch (IOException | InterruptedException e) {
        log.error("NATS connection error: {}. Attempt {}/{}", e.getMessage(), attempt, MAX_RETRIES);
        if (attempt < MAX_RETRIES) {
          try {
            Thread.sleep(Duration.ofSeconds(INITIAL_RECONNECT_DELAY_SECONDS).toMillis());
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
          }
        } else {
          log.error("Max retries reached. Failed to connect to NATS.");
        }
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
        .errorListener(natsErrorListener)
        .maxReconnects(-1)
        .reconnectWait(Duration.ofSeconds(INITIAL_RECONNECT_DELAY_SECONDS))
        .build();
  }

  private synchronized void setupOrUpdateConsumer(Connection connection)
      throws IOException, InterruptedException {
    try {
      // Close old consumer if it exists
      if (messageConsumer != null) {
        try {
          messageConsumer.close();
          log.info("Closed previous MessageConsumer.");
        } catch (Exception e) {
          log.warn("Error closing previous MessageConsumer: {}", e.getMessage());
        }
        messageConsumer = null;
      }

      final StreamContext streamContext = connection.getStreamContext("notification");

      // Get the subjects to monitor
      String[] subjects = handlerRegistry.getSupportedSubjects().toArray(String[]::new);
      if (subjects.length == 0) {
        // Default to notification.> if no specific handlers are registered
        subjects = new String[] {"notification.>"};
      }
      this.currentSubjects = subjects.clone();

      log.info("Setting up consumer for subjects: {}", Arrays.toString(subjects));

      ConsumerConfiguration.Builder consumerConfigBuilder = null;

      // Check if consumer already exists
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
    }
  }

  private void handleMessage(Message msg) {
    boolean acked = false;
    try {
      String subject = msg.getSubject();
      log.info("Received message on subject '{}'", subject);

      // Store the message for monitoring (convert to Map if it's JSON)
      storeMessage(subject, msg.getData());

      final NatsMessageHandler<?> handler = handlerRegistry.findHandlerForSubject(subject);

      if (handler == null) {
        log.warn("No handler found for subject: {}", subject);
        log.info("Message data: {}", new String(msg.getData()));
        // Process the message here as needed for unhandled subjects
      } else {
        if (handler.shouldAcknowledgeEarly()) {
          msg.ack();
          acked = true;
          handler.onMessage(msg);
        } else {
          // Ack after the handler otherwise
          handler.onMessage(msg);
        }
      }
    } catch (Exception e) {
      log.error("Error processing message: {}", e.getMessage(), e);
    } finally {
      // Ensure message is acknowledged even if there was an error, but avoid double ack
      if (!acked) {
        try {
          msg.ack();
        } catch (Exception e) {
          log.warn("Error acknowledging message: {}", e.getMessage());
        }
      }
    }
  }

  /**
   * Stores a received message in the recent messages list for monitoring purposes. Attempts to
   * parse JSON messages into Map objects.
   *
   * @param subject The subject of the message
   * @param data The raw message data
   */
  private void storeMessage(String subject, byte[] data) {
    try {
      synchronized (recentMessages) {
        Map<String, Object> messageMap;

        try {
          // Try to parse as JSON
          messageMap = objectMapper.readValue(data, Map.class);
        } catch (JsonProcessingException e) {
          // If not JSON, create a simple map with the raw data as string
          messageMap = Collections.singletonMap("data", new String(data));
        }

        // Add metadata
        messageMap.put("subject", subject);
        messageMap.put("receivedAt", System.currentTimeMillis());

        // Add to the beginning of the list
        recentMessages.addFirst(messageMap);

        // Limit size
        while (recentMessages.size() > MAX_RECENT_MESSAGES) {
          recentMessages.removeLast();
        }
      }
    } catch (Exception e) {
      log.warn("Error storing message for monitoring: {}", e.getMessage());
    }
  }

  /**
   * Gets a copy of the list of recent messages received by this consumer.
   *
   * @return List of recent messages as Maps
   */
  public List<Map<String, Object>> getRecentMessages() {
    synchronized (recentMessages) {
      return new ArrayList<>(recentMessages);
    }
  }

  /**
   * Checks if the NATS connection is established and active.
   *
   * @return true if connected, false otherwise
   */
  public boolean isConnected() {
    return natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED;
  }

  /**
   * Gets the name of the consumer if available.
   *
   * @return The consumer name or "Not Available" if not initialized
   */
  public String getConsumerName() {
    if (consumerContext != null) {
      try {
        return consumerContext.getConsumerInfo().getName();
      } catch (Exception e) {
        return "Error: " + e.getMessage();
      }
    }
    return "Not Available";
  }

  /**
   * Gets the list of subjects this consumer is monitoring.
   *
   * @return Array of subject patterns
   */
  public String[] getMonitoredSubjects() {
    return currentSubjects.clone();
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
      }
    } else {
      log.warn("NATS connection is null. Can not reinitialize consumer.");
    }
  }
}
