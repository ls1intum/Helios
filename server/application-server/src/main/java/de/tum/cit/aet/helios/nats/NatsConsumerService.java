package de.tum.cit.aet.helios.nats;

import de.tum.cit.aet.helios.github.GitHubCustomMessageHandler;
import de.tum.cit.aet.helios.github.GitHubCustomMessageHandlerRegistry;
import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.GitHubMessageHandlerRegistry;
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
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
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

  @Value("${monitoring.repositories}")
  private String[] repositoriesToMonitor;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

  private final Environment environment;

  private Connection natsConnection;
  private ConsumerContext consumerContext;

  /**
   * Active NATS message consumer.
   */
  private MessageConsumer messageConsumer;


  private final GitHubMessageHandlerRegistry handlerRegistry;

  private final GitHubCustomMessageHandlerRegistry customHandlerRegistry;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


  public NatsConsumerService(
      Environment environment,
      GitHubMessageHandlerRegistry handlerRegistry,
      GitHubCustomMessageHandlerRegistry customHandlerRegistry) {
    this.environment = environment;
    this.handlerRegistry = handlerRegistry;
    this.customHandlerRegistry = customHandlerRegistry;
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
      String[] subjects = getSubjects();
      log.info("Setting up consumer for stream 'github' with subjects: {}",
          Arrays.toString(subjects));


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
        consumerConfigBuilder = ConsumerConfiguration.builder(existingConfig)
            .filterSubjects(subjects);
      } else {
        log.info("Creating new configuration for consumer.");
        // Create new configuration with deliver policy and start time
        consumerConfigBuilder = ConsumerConfiguration.builder()
            .deliverPolicy(DeliverPolicy.ByStartTime)
            .startTime(ZonedDateTime.now().minusDays(timeframe))
            .filterSubjects(subjects);

        if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
          consumerConfigBuilder = consumerConfigBuilder.durable(durableConsumerName);
        }
      }

      ConsumerConfiguration consumerConfig = consumerConfigBuilder.build();
      consumerContext = streamContext.createOrUpdateConsumer(consumerConfig);
      log.info("Consumer created or updated with name '{}'.", consumerContext.getConsumerName());

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
      String subject = msg.getSubject();
      String lastPart = subject.substring(subject.lastIndexOf(".") + 1);

      // Check if there's a custom handler for this event type
      GitHubCustomMessageHandler<?> customHandler = customHandlerRegistry.getHandler(lastPart);
      if (customHandler != null) {
        customHandler.handleMessage(msg);
        msg.ack();
        return;
      }

      // Otherwise, use the default handler that uses GHEvent
      GHEvent eventType = GHEvent.valueOf(lastPart.toUpperCase());
      GitHubMessageHandler<?> eventHandler = handlerRegistry.getHandler(eventType);

      if (eventHandler == null) {
        log.warn("No handler found for event type: {}", eventType);
        msg.ack();
        return;
      }

      eventHandler.onMessage(msg);
      msg.ack();
    } catch (IllegalArgumentException e) {
      log.error("Invalid event type in subject '{}': {}", msg.getSubject(), e.getMessage());
    } catch (Exception e) {
      log.error("Error processing message: {}", e.getMessage(), e);
    } finally {
      msg.ack();
    }
  }

  /**
   * Re-fetch subjects and re-create/update the consumer.
   */
  public synchronized void updateSubjects() {
    if (natsConnection != null) {
      try {
        setupOrUpdateConsumer(natsConnection);
      } catch (Exception e) {
        log.error("Failed to update consumer at runtime: {}", e.getMessage(), e);
        // Schedule a retry after 5 seconds delay
        scheduler.schedule(this::updateSubjects, 5, TimeUnit.SECONDS);
      }
    }
  }

  /**
   * Subjects to monitor.
   *
   * @return The subjects to monitor.
   */
  private String[] getSubjects() {
    String[] events = Stream.concat(
            customHandlerRegistry.getSupportedEvents().stream(),
            handlerRegistry.getSupportedEvents().stream().map(GHEvent::name)
        ).map(String::toLowerCase)
        .distinct()
        .toArray(String[]::new);

    return Arrays.stream(repositoriesToMonitor)
        .map(this::getSubjectPrefix)
        .flatMap(prefix -> Arrays.stream(events).map(event -> prefix + "." + event))
        .toArray(String[]::new);
  }

  /**
   * Get subject prefix from ownerWithName for the given repository.
   *
   * @param ownerWithName The owner and name of the repository.
   * @return The subject prefix, i.e. "github.owner.name" sanitized.
   * @throws IllegalArgumentException if the repository string is improperly formatted.
   */
  private String getSubjectPrefix(String ownerWithName) {
    if (ownerWithName == null || ownerWithName.trim().isEmpty()) {
      throw new IllegalArgumentException("Repository identifier cannot be null or empty.");
    }

    String sanitized = ownerWithName.replace(".", "~");
    String[] parts = sanitized.split("/");

    if (parts.length != 2) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid repository format: '%s'. Expected format 'owner/repository'.",
              ownerWithName));
    }

    return "github." + parts[0] + "." + parts[1];
  }
}
