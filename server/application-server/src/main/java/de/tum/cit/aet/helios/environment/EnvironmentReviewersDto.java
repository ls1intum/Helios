package de.tum.cit.aet.helios.environment;

import java.util.List;
import lombok.Getter;

@Getter
public class EnvironmentReviewersDto {
  private final boolean preventSelfReview;
  private final List<Reviewer> reviewers;

  public EnvironmentReviewersDto(boolean preventSelfReview, List<Reviewer> reviewers) {
    this.preventSelfReview = preventSelfReview;
    this.reviewers = reviewers;
  }

  @Getter
  public static class Reviewer {
    private final Integer id;
    private final String login;
    private final String name; // For Team reviewers
    private final String type;

    // Constructor for User reviewers
    public Reviewer(Integer id, String login) {
      this.id = id;
      this.login = login;
      this.name = null;
      this.type = "User";
    }

    // Constructor for Team reviewers
    public Reviewer(Integer id, String name, boolean isTeam) {
      this.id = id;
      this.login = null;
      this.name = name;
      this.type = "Team";
    }
  }
}
