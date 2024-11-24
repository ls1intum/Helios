package de.tum.cit.aet.helios.nats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;

@Configuration
public class NatsConfig {

    private static final Logger logger = LoggerFactory.getLogger(NatsConfig.class);

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
            logger.info("NOpenAPI profile detected. Skipping NATS connection.");
            return null;
        }

        if (!isNatsEnabled) {
            logger.info("NATS is disabled. Skipping NATS connection.");
            return null;
        }

        Options options = Options.builder().server(natsServer).token(
            natsAuthToken.toCharArray()
        ).build();

        return Nats.connect(options);
    }
}
