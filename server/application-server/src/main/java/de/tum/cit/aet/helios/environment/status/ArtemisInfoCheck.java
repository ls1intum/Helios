package de.tum.cit.aet.helios.environment.status;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.helios.environment.Environment;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class ArtemisInfoCheck implements StatusCheckStrategy {
  private final RestTemplate restTemplate;

  @Override
  public StatusCheckResult check(Environment environment) {
    final String url = environment.getStatusUrl();
    final Map<String, Object> metadata = new HashMap<>();

    try {
      ResponseEntity<ArtemisInfo> response = restTemplate.getForEntity(
          url,
          ArtemisInfo.class);

      ArtemisInfo artemisInfo = response.getBody();

      if (artemisInfo != null) {
        ArtemisInfo.BuildInfo build = artemisInfo.build();

        metadata.put("artifact", build.artifact());
        metadata.put("name", build.name());
        metadata.put("version", build.version());
        metadata.put("group", build.group());
        metadata.put("buildTime", build.time());
      }

      return new StatusCheckResult(
          response.getStatusCode().is2xxSuccessful(),
          response.getStatusCode().value(),
          StatusCheckType.ARTEMIS_INFO,
          metadata);

    } catch (Exception e) {
      return new StatusCheckResult(false, 503, StatusCheckType.ARTEMIS_INFO, Map.of());
    }
  }

  public record ArtemisInfo(@JsonProperty("build") BuildInfo build) {
    public record BuildInfo(
        String artifact,
        String name,
        @JsonProperty("time") Instant time,
        String version,
        String group) {
    }
  }

}
