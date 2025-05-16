package de.tum.cit.aet.helios.autoconfig;

import de.tum.cit.aet.helios.HeliosClient;
import de.tum.cit.aet.helios.HeliosStatusProperties;
import de.tum.cit.aet.helios.status.listeners.BootLifecycleListener;
import de.tum.cit.aet.helios.status.listeners.HeartbeatScheduler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring-Boot autoconfiguration that wires Helios lifecycle monitoring into
 * any application that sets {@code helios.status.enabled=true}.
 *
 * <p>Beans are instantiated only when the feature flag is turned on; otherwise
 * nothing is registered and the library stays idle.
 */
@AutoConfiguration
@EnableConfigurationProperties(HeliosStatusProperties.class)
public class HeliosStatusAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(HeliosStatusAutoConfiguration.class);

  private final HeliosStatusProperties props;

  public HeliosStatusAutoConfiguration(HeliosStatusProperties props) {
    this.props = props;
  }

  /**
   * Log a single line at startup so operators can see whether monitoring runs.
   */
  @PostConstruct
  public void logStatus() {
    if (props.enabled()) {
      log.info("Helios status push is enabled – lifecycle monitoring will start.");
    } else {
      log.info("Helios status push is disabled – no lifecycle updates will be sent.");
    }
  }

  /**
   * Validates the Helios configuration once the application context is built,
   * failing fast if mandatory information is missing.
   */
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

  /**
   * Http client wrapper that all listeners will share.
   */
  @Bean
  HeliosClient heliosClient(HeliosStatusProperties props) {
    return new HeliosClient(props);
  }

  /**
   * Publishes lifecycle events for {@code ApplicationStarted/Ready/Failed}.
   */
  @Bean
  public BootLifecycleListener bootLifecycleListener(HeliosClient helios) {
    return props.enabled() ? new BootLifecycleListener(helios) : null;
  }

  /**
   * Schedules the periodic heartbeat while the app is alive.
   */
  @Bean
  public HeartbeatScheduler heartbeatScheduler(HeliosClient helios) {
    return props.enabled() ? new HeartbeatScheduler(helios, props) : null;
  }
}