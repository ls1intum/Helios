package de.tum.cit.aet.helios.common.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
public class NatsConfig {

  @Value("${nats.enabled}")
  private boolean isNatsEnabled;

  @Value("${nats.server}")
  private String natsServer;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

  private final Environment environment;

  @Bean
  Connection natsConnection() throws Exception {
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
  ObjectMapper objectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }
}
