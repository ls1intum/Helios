package de.tum.cit.aet.helios.tests;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class TestProcessorClient {
  private final RestTemplate restTemplate;

  public TestProcessorClient() {
    this.restTemplate =
        new RestTemplateBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .readTimeout(Duration.ofSeconds(3))
            .build();
  }

  @Value("${helios.testProcessor.apiUrl}")
  private String apiUrl;

  public List<de.tum.cit.aet.helios.common.dto.test.TestSuite> getTestResults(Long workflowRunId) {
    String url = apiUrl + "/test-results/" + workflowRunId;
    ResponseEntity<List<de.tum.cit.aet.helios.common.dto.test.TestSuite>> response =
        restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<
                List<de.tum.cit.aet.helios.common.dto.test.TestSuite>>() {});

    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new RuntimeException("Failed to fetch test results for workflow run " + workflowRunId);
    }

    return response.getBody();
  }
}
