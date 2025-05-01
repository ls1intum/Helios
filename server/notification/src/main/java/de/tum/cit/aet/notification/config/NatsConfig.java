package de.tum.cit.aet.notification.config;

import de.tum.cit.aet.notification.nats.NatsErrorListener;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

@Configuration
@Slf4j
public class NatsConfig {

  @Value("${nats.enabled}")
  private boolean isNatsEnabled;

  @Value("${nats.server}")
  private String natsServer;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

  private final Environment environment;

  private NatsErrorListener natsErrorListener;

  public NatsConfig(Environment environment) {
    this.environment = environment;
  }

  @Autowired
  public void setNatsErrorListener(@Lazy NatsErrorListener natsErrorListener) {
    this.natsErrorListener = natsErrorListener;
  }

  @Bean
  public Connection natsConnection() throws Exception {
    if (!isNatsEnabled) {
      log.info("NATS is disabled. Skipping NATS connection.");
      return null;
    }

    Options.Builder optionsBuilder =
        Options.builder()
            .server(natsServer)
            .token(natsAuthToken.toCharArray())
            .connectionListener(
                (conn, type) ->
                    log.info(
                        "Connection event - Server: {}, {}", conn.getServerInfo().getPort(), type))
            .maxReconnects(-1)
            .reconnectWait(Duration.ofSeconds(2));

    // Add error listener if available
    if (natsErrorListener != null) {
      optionsBuilder.errorListener(natsErrorListener);
    }

    Options options = optionsBuilder.build();

    return Nats.connect(options);
  }
}
