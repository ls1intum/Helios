package de.tum.cit.aet.helios.nats;

import de.tum.cit.aet.helios.github.GitHubCustomMessageHandler;
import de.tum.cit.aet.helios.github.GitHubCustomMessageHandlerRegistry;
import de.tum.cit.aet.helios.github.GitHubFacade;
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
import io.sentry.Sentry;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.springframework.beans.factory.annotation.Autowired;
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

  /**
   * Active NATS message consumer.
   */
  private MessageConsumer messageConsumer;

  private final Environment environment;

  private final GitHubMessageHandlerRegistry handlerRegistry;

  private final GitHubCustomMessageHandlerRegistry customHandlerRegistry;

  private final GitHubFacade gitHub;

  private final NatsErrorListener natsErrorListener;

  @Autowired
  public NatsConsumerService(
      Environment environment,
      GitHubMessageHandlerRegistry handlerRegistry,
      @Lazy GitHubCustomMessageHandlerRegistry customHandlerRegistry,
      GitHubFacade gitHub,
      @Lazy NatsErrorListener natsErrorListener) {
    this.environment = environment;
    this.handlerRegistry = handlerRegistry;
    this.customHandlerRegistry = customHandlerRegistry;
    this.gitHub = gitHub;
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
            .inactiveThreshold(Duration.ofMinutes(consumerInactiveThresholdMinutes))
            .ackWait(Duration.ofSeconds(consumerAckWaitSeconds))
            .filterSubjects(subjects);
      } else {
        log.info("Creating new configuration for consumer.");
        // Create new configuration with deliver policy and start time
        consumerConfigBuilder = ConsumerConfiguration.builder()
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
      log.info("Consumer created or updated with name '{}' and configuration: {}",
          consumerContext.getConsumerInfo().getName(), consumerConfig);

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


      log.info("Received message with subject: {}", subject);

      // Check if there's a custom handler for this event type
      GitHubCustomMessageHandler<?> customHandler = customHandlerRegistry.getHandler(lastPart);
      if (customHandler != null) {
        if (customHandler.isGlobalEvent()) {
          // Acknowledge the message early if it is a global event.
          // Currently, the only global event is `installation_repositories`.
          // The handler for this event initiates data synchronization for added repositories
          // and deletes removed repositories. Early acknowledgment prevents message redelivery
          // or the durable consumer from being dropped by NATS.
          msg.ack();
          customHandler.onMessage(msg);
        } else {
          // Late ack otherwise
          customHandler.onMessage(msg);
          msg.ack();
        }
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

      if (eventHandler.isGlobalEvent()) {
        // Acknowledge the message early if it is a global event.
        // Currently, the only global event is `installation_repositories`.
        // The handler for this event initiates data synchronization for added repositories
        // and deletes removed repositories. Early acknowledgment prevents message redelivery
        // or the durable consumer from being dropped by NATS.
        msg.ack();
        eventHandler.onMessage(msg);
      } else {
        // Late ack otherwise
        eventHandler.onMessage(msg);
        msg.ack();
      }
    } catch (IllegalArgumentException e) {
      log.error("Invalid event type in subject '{}': {}", msg.getSubject(), e.getMessage());
    } catch (Exception e) {
      log.error("Error processing message: {}", e.getMessage(), e);
    } finally {
      msg.ack();
    }
  }

  /**
   * Reinitialize the NATS consumer.
   */
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

  /**
   * Retrieves all subjects to monitor by combining
   * custom event subjects and normal GitHub event subjects.
   *
   * @return an array of unique subjects.
   */
  private String[] getSubjects() {
    // Retrieve the list of repositories; if an error occurs, an empty list is returned.
    List<String> repositories = getInstalledRepositories();
    if (repositories.isEmpty()) {
      return new String[0];
    }

    // Build streams for custom events and normal events.
    Stream<String> customSubjects = getCustomEventSubjects(repositories);
    Stream<String> normalSubjects = getNormalEventSubjects(repositories);

    // Combine, remove duplicates, and convert to array.
    return Stream.concat(customSubjects, normalSubjects)
        .distinct()
        .toArray(String[]::new);
  }

  /**
   * Retrieves the list of installed repositories for the GitHub App.
   *
   * @return a list of repository names, or an empty list if an error occurs.
   */
  private List<String> getInstalledRepositories() {
    try {
      return gitHub.getInstalledRepositoriesForGitHubApp();
    } catch (IOException e) {
      log.error("Failed to get installed repositories for GitHub App: {}", e.getMessage(), e);
      Sentry.captureException(e);
      return Collections.emptyList();
    }
  }

  /**
   * Builds a stream of subjects for custom events.
   *
   * @param repositories the list of repositories to include when the event is not global.
   * @return a stream of custom event subjects.
   */
  private Stream<String> getCustomEventSubjects(List<String> repositories) {
    return customHandlerRegistry.getSupportedEvents().stream()
        .flatMap(customEvent -> {
          GitHubCustomMessageHandler<?> customHandler =
              customHandlerRegistry.getHandler(customEvent);
          if (customHandler == null) {
            return Stream.empty();
          }
          String eventName = customEvent.toLowerCase();

          // If the event is global, use a wildcard; otherwise, build a subject for each repository.
          return customHandler.isGlobalEvent()
              ? Stream.of(buildGlobalSubject(eventName))
              : repositories.stream().map(repo -> buildRepositorySubject(repo, eventName));
        });
  }

  /**
   * Builds a stream of subjects for normal GitHub events.
   *
   * @param repositories the list of repositories to include when the event is not global.
   * @return a stream of normal event subjects.
   */
  private Stream<String> getNormalEventSubjects(List<String> repositories) {
    return handlerRegistry.getSupportedEvents().stream()
        .flatMap(event -> {
          GitHubMessageHandler<?> handler = handlerRegistry.getHandler(event);
          if (handler == null) {
            return Stream.empty();
          }
          String eventName = event.name().toLowerCase();

          // If the event is global, use a wildcard; otherwise, build a subject for each repository.
          return handler.isGlobalEvent()
              ? Stream.of(buildGlobalSubject(eventName))
              : repositories.stream().map(repo -> buildRepositorySubject(repo, eventName));
        });
  }

  /**
   * Constructs a subject for global events.
   *
   * <p>This method returns a subject string formatted as:
   * <code>github.*.*.&lt;eventName&gt;</code>.</p>
   *
   * @param eventName the name of the event.
   * @return a subject string for global events, e.g.
   *      <code>github.*.*.push</code> when <code>eventName</code> is "push".
   */
  private String buildGlobalSubject(String eventName) {
    return "github.*.*." + eventName;
  }

  /**
   * Constructs a subject string specific to a repository and event.
   *
   * <p>This method formats the subject as:
   * <code>&lt;subjectPrefix&gt;.&lt;eventName&gt;</code>,
   * where the subject prefix is generated based on the
   * repository identifier provided.</p>
   *
   * @param repo      the repository identifier in the format <code>"owner/repository"</code>.
   * @param eventName the name of the event.
   * @return the fully constructed subject string. For example, if
   *      <code>eventName</code> is <code>"push"</code>, then the returned subject will be
   *      <code>github.myOrg.myRepo.push</code>.
   */
  private String buildRepositorySubject(String repo, String eventName) {
    return getSubjectPrefix(repo) + "." + eventName;
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
