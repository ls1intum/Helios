package de.tum.cit.aet.helios.deploymentprotection;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * The payload of the deployment_protection_rule event from GitHub.
 * Payload has minimal fields required for the handler to process the event.
 * The additional fields can be added as needed.
 * org.kohsuke.github library did not support this event at the time of implementation.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DeploymentProtectionRulePayload {

  // "requested"
  private String action;

  private String environment;

  private String event;

  /**
   * The URL to accept or reject the deployment.
   */
  @JsonProperty("deployment_callback_url")
  private String deploymentCallbackUrl;

  private Deployment deployment;

  private Repository repository;

  private Sender sender;

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
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @ToString
  static class Repository {
    private long id;

    @JsonProperty("node_id")
    private String nodeId;

    private String name;

    @JsonProperty("full_name")
    private String fullName;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @ToString
  static class Sender {
    private String login;

    private long id;

    @JsonProperty("node_id")
    private String nodeId;

    @Enumerated(EnumType.STRING)
    private Type type;

    public enum Type {
      User,
      Bot
    }
  }
}
