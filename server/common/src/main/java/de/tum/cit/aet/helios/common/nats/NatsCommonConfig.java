package de.tum.cit.aet.helios.common.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@Log4j2
@RequiredArgsConstructor
public class NatsCommonConfig {

  @Value("${nats.enabled}")
  private boolean isNatsEnabled;

  @Value("${nats.server}")
  private String natsServer;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

  private final Environment environment;

  @Bean
  public Connection natsConnection() throws Exception {
    if (environment.acceptsProfiles(Profiles.of("openapi"))) {
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

  @Bean
  public NatsErrorListener natsErrorListener(BaseNatsConsumerService natsConsumerService) {
    return new NatsErrorListener(natsConsumerService);
  }
}
