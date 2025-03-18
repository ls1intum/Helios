package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.common.nats.NatsCommonConfig;
import de.tum.cit.aet.helios.common.nats.NatsMessageHandler;
import de.tum.cit.aet.helios.common.nats.NatsMessageHandlerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(NatsCommonConfig.class)
public class NatsConfig {
  @Bean
  public NatsMessageHandlerRegistry natsMessageHandlerRegistry(NatsMessageHandler<?>[] handlers) {
    return new NatsMessageHandlerRegistry(handlers);
  }
}
