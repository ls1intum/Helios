package de.tum.cit.aet.helios.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@Log4j2
public class NatsConfig {

  @Value("${nats.enabled}")
  private boolean isNatsEnabled;

  @Value("${nats.server}")
  private String natsServer;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

  private final Environment environment;

  public NatsConfig(Environment env) {
    this.environment = env;
  }

  @Bean
  public Connection natsConnection() throws Exception {
    if (environment.matchesProfiles("openapi")) {
      log.info("No OpenAPI profile detected. Skipping NATS connection.");
      return null;
    }

    if (!isNatsEnabled) {
      log.info("NATS is disabled. Skipping NATS connection.");
      return null;
    }

    Options options =
        Options.builder().server(natsServer).token(natsAuthToken.toCharArray()).build();

    return Nats.connect(options);
  }
}
