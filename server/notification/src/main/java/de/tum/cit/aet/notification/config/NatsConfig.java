package de.tum.cit.aet.notification.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NatsConfig {

  @Value("${nats.server}")
  private String natsServer;

  @Value("${nats.auth.token}")
  private String natsAuthToken;

  @Bean
  public Connection natsConnection() throws Exception {

    Options options = Options.builder().server(natsServer).token(natsAuthToken).build();
    return Nats.connect(options);
  }
}
