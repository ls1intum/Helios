package de.tum.cit.aet.helios.deploymentprotection;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHDeployment;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

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

  private GHDeployment deployment;

  @JsonProperty("pull_requests")
  private List<GHPullRequest> pullRequests;

  private GHRepository repository;

  private GHOrganization organization;

  private GHAppInstallation installation;

  // GHUser throws an exception
  // Conflicting setter definitions for property "assignees"
  private Sender sender;

  /**
   * Represents the "sender" object in the payload.
   */
  @Getter
  @Setter
  @NoArgsConstructor
  @ToString
  static class Sender {
    private String login;
    private long id;

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("gravatar_id")
    private String gravatarId;

    private String url;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("followers_url")
    private String followersUrl;

    @JsonProperty("following_url")
    private String followingUrl;

    @JsonProperty("gists_url")
    private String gistsUrl;

    @JsonProperty("starred_url")
    private String starredUrl;

    @JsonProperty("subscriptions_url")
    private String subscriptionsUrl;

    @JsonProperty("organizations_url")
    private String organizationsUrl;

    @JsonProperty("repos_url")
    private String reposUrl;

    @JsonProperty("events_url")
    private String eventsUrl;

    @JsonProperty("received_events_url")
    private String receivedEventsUrl;

    private String type;

    @JsonProperty("user_view_type")
    private String userViewType;

    @JsonProperty("site_admin")
    private boolean siteAdmin;
  }

}
