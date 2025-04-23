package de.tum.cit.aet.helios.environment.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubEnvironmentProtectionRuleDto {
  private Long id;

  @JsonProperty("node_id")
  private String nodeId;

  private String type;

  @JsonProperty("wait_timer")
  private Integer waitTimer;

  @JsonProperty("prevent_self_review")
  private Boolean preventSelfReview;

  private List<ReviewerContainer> reviewers;

  @JsonProperty("branch_policy")
  private BranchPolicy branchPolicy;

  @Getter
  @Setter
  public static class ReviewerContainer {
    private String type; // "User" or "Team"
    private Reviewer reviewer;
  }

  @Getter
  @Setter
  public static class Reviewer {
    private Integer id;
    private String login; // For User
    private String name; // For Team
    private String type; // "User" or "Team"
  }

  @Getter
  @Setter
  public static class BranchPolicy {
    @JsonProperty("protected_branches")
    private Boolean protectedBranches;

    @JsonProperty("custom_branch_policies")
    private Boolean customBranchPolicies;

    @JsonProperty("allowed_branches")
    private List<String> allowedBranches;
  }
}
