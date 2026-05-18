package de.tum.cit.aet.helios.workflow.queue.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * GitHub {@code self_hosted_runner} webhook payload.
 *
 * <p>Org-level event. {@code action} is one of {@code created} / {@code online} / {@code offline} /
 * {@code removed}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubSelfHostedRunnerPayload(
    String action, SelfHostedRunner selfHostedRunner, Organization organization) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record SelfHostedRunner(
      Long id,
      String name,
      String os,
      String status,
      Boolean busy,
      List<RunnerLabel> labels,
      RunnerGroup runnerGroup) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record RunnerLabel(Long id, String name, String type) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record RunnerGroup(Long id, String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Organization(Long id, String login) {}
}
