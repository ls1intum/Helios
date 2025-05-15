package de.tum.cit.aet.helios.autoconfig;

import de.tum.cit.aet.helios.HeliosClient;
import de.tum.cit.aet.helios.HeliosStatusProperties;
import de.tum.cit.aet.helios.status.listeners.BootLifecycleListener;
import de.tum.cit.aet.helios.status.listeners.FlywayCallback;
import de.tum.cit.aet.helios.status.listeners.HeartbeatScheduler;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(HeliosStatusProperties.class)
@ConditionalOnProperty(prefix = "helios.status", name = "enabled", havingValue = "true")
@Import({BootLifecycleListener.class, HeartbeatScheduler.class, FlywayCallback.class})
public class HeliosStatusAutoConfiguration {
  @Bean
  ApplicationRunner validateHeliosCfg(HeliosStatusProperties p) {
    return args -> {
      if (p.enabled() && (p.environmentName() == null
          || p.environmentName().isBlank()
          || p.endpoints() == null
          || p.endpoints().isEmpty())) {
        throw new IllegalStateException("""
            helios.status.enabled=true but environment-name or endpoints missing.
            Each endpoint needs url + secretKey.""");
      }
    };
  }

  @Bean
  HeliosClient heliosClient(HeliosStatusProperties props) {
    return new HeliosClient(props);
  }
}