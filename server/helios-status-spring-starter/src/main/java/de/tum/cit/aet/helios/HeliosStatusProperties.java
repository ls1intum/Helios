package de.tum.cit.aet.helios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "helios.status")
@Validated
public record HeliosStatusProperties(
    boolean enabled,                       // default false
    @NotBlank String environmentName,    // app name
    @NotEmpty List<HeliosEndpoint> endpoints,     // url + secretKey
    @DurationUnit(ChronoUnit.SECONDS)
    @Positive Duration heartbeatInterval     // default 30 s
) {
  /**
   * Supply defaults when properties missing.
   */
  public HeliosStatusProperties {
    if (heartbeatInterval == null) {
      heartbeatInterval = Duration.ofSeconds(30);
    }
  }
}
