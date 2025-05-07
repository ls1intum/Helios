package de.tum.cit.aet.helios.autoconfig;

import de.tum.cit.aet.helios.HeliosClient;
import de.tum.cit.aet.helios.HeliosStatusProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HeliosStatusProperties.class)
@ConditionalOnProperty(prefix = "helios.status", name = "enabled", havingValue = "true")
public class HeliosStatusAutoConfiguration {
  @Bean
  ApplicationRunner validateHeliosCfg(HeliosStatusProperties p) {
    return args -> {
      if (p.enabled() && (p.urls() == null || p.urls().isEmpty() || p.secretKey() == null)) {
        throw new IllegalStateException("""
                helios.status.enabled=true but urls/secretKey missing.
                Define them in application.yml or environment variables.""");
      }
    };
  }

  @Bean
  HeliosClient heliosClient(HeliosStatusProperties props) {
    return new HeliosClient(props);
  }
}