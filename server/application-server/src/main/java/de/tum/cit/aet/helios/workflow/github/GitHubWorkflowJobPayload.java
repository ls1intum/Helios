package de.tum.cit.aet.helios.workflow.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubWorkflowJobPayload(
    String action,
    WorkflowJob workflowJob,
    Deployment deployment,
    Repository repository) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record WorkflowJob(
      Long id,
      Long runId,
      String workflowName,
      String headBranch,
      String headSha,
      String htmlUrl,
      String status,
      String conclusion,
      OffsetDateTime createdAt,
      OffsetDateTime startedAt,
      OffsetDateTime completedAt,
      String name,
      List<String> labels,
      Long runnerId,
      String runnerName,
      Long runnerGroupId,
      String runnerGroupName) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Deployment(
      Long id,
      String environment,
      String ref,
      String sha,
      OffsetDateTime createdAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Repository(
      Long id,
      String fullName) {}
}
