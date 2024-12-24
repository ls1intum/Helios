package de.tum.cit.aet.helios.issue;

import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "issue")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "issue_type", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "ISSUE")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Issue extends BaseGitServiceEntity {

  private int number;

  @NonNull
  @Enumerated(EnumType.STRING)
  private State state;

  @NonNull private String title;

  @Lob private String body;

  @NonNull private String htmlUrl;

  private boolean isLocked;

  private OffsetDateTime closedAt;

  private int commentsCount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id")
  @ToString.Exclude
  private User author;

  @ManyToMany
  @JoinTable(
      name = "issue_assignee",
      joinColumns = @JoinColumn(name = "issue_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @ToString.Exclude
  private Set<User> assignees = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_id")
  private GitRepository repository;

  public enum State {
    OPEN,
    CLOSED
  }

  public boolean isPullRequest() {
    return false;
  }

  // Missing properties
  // - milestone
  // - issue_comments
  // - labels

  // Ignored GitHub properties:
  // - closed_by seems not to be used by webhooks
  // - author_association (not provided by our GitHub API client)
  // - state_reason
  // - reactions
  // - active_lock_reason
  // - [remaining urls]
}
