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
 * Autoconfiguration class that wires Helios lifecycle monitoring into Spring Boot apps.
 *
 * <p>Enabled by setting {@code helios.status.enabled=true}. Registers beans for the
 * lifecycle event listener, heartbeat scheduler, and core Helios client.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(HeliosStatusProperties.class)
public class HeliosStatusAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(HeliosStatusAutoConfiguration.class);

  private final HeliosStatusProperties props;

  /**
   * Constructs the configuration with the given Helios status properties.
   *
   * @param props the Helios status properties
   */
  public HeliosStatusAutoConfiguration(HeliosStatusProperties props) {
    this.props = props;
  }

  /**
   * Logs whether Helios lifecycle monitoring is enabled after context initialization.
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
   * Fails fast if Helios monitoring is enabled but required fields are missing.
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
   * Initializes and provides the shared Helios HTTP client bean.
   */
  @Bean
  HeliosClient heliosClient(HeliosStatusProperties props) {
    return new HeliosClient(props);
  }

  /**
   * Publishes Spring Boot application lifecycle events to Helios.
   */
  @Bean
  public BootLifecycleListener bootLifecycleListener(HeliosClient helios) {
    return props.enabled() ? new BootLifecycleListener(helios) : null;
  }

  /**
   * Schedules and sends regular heartbeats while the application is alive.
   */
  @Bean
  public HeartbeatScheduler heartbeatScheduler(HeliosClient helios) {
    return props.enabled() ? new HeartbeatScheduler(helios, props) : null;
  }
}