package de.tum.cit.aet.helios.nats;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.GitHubMessageHandlerRegistry;
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
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
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

  private final GitHubMessageHandlerRegistry handlerRegistry;

  public NatsConsumerService(
      Environment environment, GitHubMessageHandlerRegistry handlerRegistry) {
    this.environment = environment;
    this.handlerRegistry = handlerRegistry;
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
        setupConsumer(natsConnection);
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

  private void setupConsumer(Connection connection) throws IOException, InterruptedException {
    try {
      StreamContext streamContext = connection.getStreamContext("github");

      // Check if consumer already exists
      if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
        try {
          consumerContext = streamContext.getConsumerContext(durableConsumerName);
        } catch (JetStreamApiException e) {
          consumerContext = null;
        }
      }

      if (consumerContext == null) {
        log.info("Setting up consumer for subjects: {}", Arrays.toString(getSubjects()));
        ConsumerConfiguration.Builder consumerConfigBuilder =
            ConsumerConfiguration.builder()
                .filterSubjects(getSubjects())
                .deliverPolicy(DeliverPolicy.ByStartTime)
                .startTime(ZonedDateTime.now().minusDays(timeframe));

        if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
          consumerConfigBuilder.durable(durableConsumerName);
        }

        ConsumerConfiguration consumerConfig = consumerConfigBuilder.build();
        consumerContext = streamContext.createOrUpdateConsumer(consumerConfig);
      } else {
        log.info("Consumer already exists. Skipping consumer setup.");
      }

      MessageHandler handler = this::handleMessage;
      consumerContext.consume(handler);
      log.info("Successfully started consuming messages.");
    } catch (JetStreamApiException e) {
      log.error("JetStream API exception: {}", e.getMessage(), e);
      throw new IOException("Failed to set up consumer.", e);
    }
  }

  private void handleMessage(Message msg) {
    try {
      String subject = msg.getSubject();
      String lastPart = subject.substring(subject.lastIndexOf(".") + 1);
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
   * Subjects to monitor.
   *
   * @return The subjects to monitor.
   */
  private String[] getSubjects() {
    String[] events =
        handlerRegistry.getSupportedEvents().stream()
            .map(GHEvent::name)
            .map(String::toLowerCase)
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
