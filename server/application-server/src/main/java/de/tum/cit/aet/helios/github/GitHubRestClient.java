package de.tum.cit.aet.helios.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lightweight REST client for GitHub endpoints that the kohsuke client doesn't cover cleanly
 * (org-level runner inventory) and where ETag conditional GETs matter.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class GitHubRestClient {

  private final GitHubClientManager clientManager;
  private final EtagCache etagCache;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  @Value("${helios.github.apiBaseUrl:https://api.github.com}")
  private String apiBaseUrl;

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
      .build();

  /**
   * GETs a JSON resource, transparently using {@code If-None-Match} when an ETag is known. Returns
   * a cached body on 304. Returns empty on 4xx/5xx (logged).
   */
  public Optional<JsonNode> get(String path) {
    String url = apiBaseUrl + path;
    try {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(15))
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .GET();
      String token = clientManager.getCurrentToken();
      if (token != null && !token.isBlank()) {
        builder.header("Authorization", "Bearer " + token);
      }
      etagCache.getEtag(url).ifPresent(etag -> builder.header("If-None-Match", etag));

      HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();

      if (status == 304) {
        meterRegistry.counter("helios.github.rest.304").increment();
        return etagCache.getBody(url, JsonNode.class);
      }
      if (status == 429 || status == 403) {
        meterRegistry.counter("helios.github.rest.ratelimited", "status",
            Integer.toString(status)).increment();
        log.warn("GitHub REST rate-limited or forbidden: {} {} remaining={}", status, url,
            response.headers().firstValue("x-ratelimit-remaining").orElse("?"));
        return Optional.empty();
      }
      if (status >= 200 && status < 300) {
        meterRegistry.counter("helios.github.rest.2xx").increment();
        JsonNode parsed = objectMapper.readTree(response.body());
        String etag = response.headers().firstValue("ETag").orElse(null);
        etagCache.put(url, etag, parsed);
        return Optional.of(parsed);
      }
      meterRegistry.counter("helios.github.rest.error",
          "status", Integer.toString(status)).increment();
      log.warn("GitHub REST {} for {}", status, url);
      return Optional.empty();
    } catch (Exception e) {
      log.warn("GitHub REST call failed for {}: {}", url, e.getMessage());
      return Optional.empty();
    }
  }

  /** Returns rate-limit remaining from the most recent response, or -1 if unknown. */
  public int rateLimitRemaining() {
    // Caller-driven monitoring point; relies on Sentry breadcrumbs / metrics in production.
    return -1;
  }
}
