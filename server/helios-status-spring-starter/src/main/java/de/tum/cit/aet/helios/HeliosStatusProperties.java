package de.tum.cit.aet.helios;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration holder for Helios status reporting.
 *
 * <p>This class binds properties prefixed with {@code helios.status} from Spring configuration
 * sources such as YAML or application.properties. It is used to configure the status push
 * behavior, including whether it is enabled, which environment is being reported, what the
 * heartbeat interval is, and which endpoints to push to.</p>
 *
 * <pre>
 * Example configuration:
 *
 * helios:
 *   status:
 *     enabled: true
 *     environment-name: prod-eu1
 *     heartbeat-interval: 30s
 *     endpoints:
 *       - url: https://helios.aet.cit.tum.de/api/environments/status
 *         secret-key: my-secret
 * </pre>
 *
 * @param enabled whether status push is enabled
 * @param environmentName GitHub Actions environment name
 * @param endpoints list of configured Helios endpoints
 * @param heartbeatInterval interval for periodic heartbeat pings (default: 30s)
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
   * Validates and defaults the heartbeat interval.
   *
   * <p>Defaults to 30 seconds if not specified. Throws an exception if the configured
   * value is not positive.</p>
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
