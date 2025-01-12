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
import org.kohsuke.github.GHUser;

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

  private GHUser sender;

}
