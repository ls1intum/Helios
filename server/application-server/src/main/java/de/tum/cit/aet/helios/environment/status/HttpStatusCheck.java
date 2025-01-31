package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.environment.Environment;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class HttpStatusCheck implements StatusCheckStrategy {
  private final RestTemplate restTemplate;

  @Override
  public StatusCheckResult check(Environment environment) {
    try {
      ResponseEntity<Void> response = restTemplate.getForEntity(
          environment.getStatusUrl(),
          Void.class);

      return new StatusCheckResult(
          response.getStatusCode().is2xxSuccessful(),
          response.getStatusCode().value(),
          StatusCheckType.HTTP_STATUS,
          Map.of());
    } catch (Exception e) {
      return new StatusCheckResult(false, 503, StatusCheckType.HTTP_STATUS, Map.of());
    }
  }

}
