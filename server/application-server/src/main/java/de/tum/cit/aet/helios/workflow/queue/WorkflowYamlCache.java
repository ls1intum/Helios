package de.tum.cit.aet.helios.workflow.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.helios.github.GitHubRestClient;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/**
 * Caches parsed workflow YAML by {@code (fullName, headSha, path)}. Used by the stuck classifier to
 * detect {@code concurrency:} blocks. See plan §C2.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class WorkflowYamlCache {

  private final GitHubRestClient restClient;

  private final Cache<String, Optional<WorkflowYaml>> cache =
      Caffeine.newBuilder()
          .expireAfterWrite(Duration.ofHours(1))
          .maximumSize(2_000)
          .build();

  /**
   * Fetches and parses {@code .github/workflows/<file>} at {@code sha}. Returns empty if the file
   * isn't reachable or doesn't parse.
   */
  public Optional<WorkflowYaml> fetch(String fullName, String sha, String workflowPath) {
    if (fullName == null || sha == null || workflowPath == null) {
      return Optional.empty();
    }
    String key = fullName + "@" + sha + ":" + workflowPath;
    Optional<WorkflowYaml> cached = cache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }
    Optional<WorkflowYaml> result = load(fullName, sha, workflowPath);
    cache.put(key, result);
    return result;
  }

  private Optional<WorkflowYaml> load(String fullName, String sha, String workflowPath) {
    String path = "/repos/" + fullName + "/contents/" + workflowPath + "?ref=" + sha;
    Optional<JsonNode> body = restClient.get(path);
    if (body.isEmpty()) {
      return Optional.empty();
    }
    JsonNode node = body.get();
    String contentB64 = node.path("content").asText("");
    if (contentB64.isBlank()) {
      return Optional.empty();
    }
    String decoded;
    try {
      decoded = new String(java.util.Base64.getMimeDecoder().decode(contentB64));
    } catch (IllegalArgumentException e) {
      log.warn("Workflow YAML base64 decode failed for {}", path);
      return Optional.empty();
    }
    try {
      Yaml yaml = new Yaml();
      Object parsed = yaml.load(decoded);
      return Optional.of(new WorkflowYaml(parsed, extractConcurrencyGroup(parsed)));
    } catch (Exception e) {
      log.warn("Workflow YAML parse failed for {}: {}", path, e.getMessage());
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private String extractConcurrencyGroup(Object parsed) {
    if (!(parsed instanceof java.util.Map<?, ?> map)) {
      return null;
    }
    Object concurrency = ((java.util.Map<String, Object>) map).get("concurrency");
    if (concurrency instanceof String s) {
      return s;
    }
    if (concurrency instanceof java.util.Map<?, ?> cmap) {
      Object group = ((java.util.Map<String, Object>) cmap).get("group");
      return group == null ? null : group.toString();
    }
    return null;
  }

  /** Parsed workflow file with the top-level concurrency group expression, if any. */
  public record WorkflowYaml(Object raw, String concurrencyGroupExpression) {}
}
