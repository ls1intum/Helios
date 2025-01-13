package de.tum.cit.aet.helios.deploymentprotection;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class DeploymentProtectionRulePayload {

  // "requested"
  private String action;

  private String environment;

  private String event;

  @JsonProperty("deployment_callback_url")
  private String deploymentCallbackUrl;

  private Deployment deployment;

  @Getter
  @Setter
  @NoArgsConstructor
  @ToString
  static class Deployment {
    private String url;
    private long id;

    @JsonProperty("node_id")
    private String nodeId;

    private String task;

    @JsonProperty("original_environment")
    private String originalEnvironment;

    private String environment;
    private String description;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("repository_url")
    private String repositoryUrl;

    private String sha;

    // Branch name
    private String ref;

    @JsonProperty("performed_via_github_app")
    private PerformedViaGithubApp performedViaGithubApp;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @ToString
  static class PerformedViaGithubApp {
    private long id;

    @JsonProperty("client_id")
    private String clientId;

    // For manual triggering, this is 'github-actions'
    private String slug;

    @JsonProperty("node_id")
    private String nodeId;

    private String name;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @ToString
  public static class PullRequest {

    private String url;
    private long id;

    @JsonProperty("node_id")
    private String nodeId;

    private int number;
    private String title;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("closed_at")
    private String closedAt;

    @JsonProperty("merged_at")
    private String mergedAt;
  }
}
