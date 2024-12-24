package de.tum.cit.aet.helios.user;

import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "user", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class User extends BaseGitServiceEntity {

  @NonNull private String login;

  @NonNull private String avatarUrl;

  // AKA bio
  private String description;

  @NonNull
  // Equals login if not fetched / existing
  private String name;

  private String company;

  // Url
  private String blog;

  private String location;

  private String email;

  @NonNull private String htmlUrl;

  @NonNull
  @Enumerated(EnumType.STRING)
  private Type type;

  private int followers;

  private int following;

  public enum Type {
    USER,
    ORGANIZATION,
    BOT
  }

  // Missing properties:
  // - createdIssues
  // - assignedIssues
  // - issueComments
  // - mergedPullRequests
  // - requestedPullRequestReviews
  // - reviews
  // - reviewComments

  // Ignored GitHub properties:
  // - totalPrivateRepos
  // - ownedPrivateRepos
  // - publicRepos
  // - publicGists
  // - privateGists
  // - collaborators
  // - is_verified (org?)
  // - disk_usage
  // - suspended_at (user)
  // - twitter_username
  // - billing_email (org)
  // - has_organization_projects (org)
  // - has_repository_projects (org)
}
