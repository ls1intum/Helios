package de.tum.cit.aet.helios;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code helios.status.*} configuration from YAML / properties files.
 *
 * <pre>
 * helios:
 *   status:
 *     enabled: true
 *     environment-name: prod-eu1
 *     heartbeat-interval: 30s
 *     endpoints:
 *       - url: https://helios.aet.cit.tum.de/api/environments/status
 *         secret-key: verySecret
 * </pre>
 */
@ConfigurationProperties(prefix = "helios.status")
@Validated
public record HeliosStatusProperties(
    // default false
    boolean enabled,
    String environmentName,
    List<HeliosEndpoint> endpoints,
    // default 30s
    @DurationUnit(ChronoUnit.SECONDS)
    Duration heartbeatInterval
) {
  /**
   * Supply defaults when properties missing.
   */
  public HeliosStatusProperties {
    if (heartbeatInterval == null) {
      heartbeatInterval = Duration.ofSeconds(30);
    }
    if (!heartbeatInterval.isPositive()) {
      throw new IllegalArgumentException("helios.status.heartbeat-interval must be positive");
    }
  }
}
