package de.tum.cit.aet.helios.issue;

import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import de.tum.cit.aet.helios.label.Label;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
      name = "issue_label",
      joinColumns = @JoinColumn(name = "issue_id"),
      inverseJoinColumns = @JoinColumn(name = "label_id")
  )
  @ToString.Exclude
  private Set<Label> labels = new HashSet<>();

  @ManyToMany
  @JoinTable(
      name = "issue_assignee",
      joinColumns = @JoinColumn(name = "issue_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @ToString.Exclude
  private Set<User> assignees = new HashSet<>();

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

  // Ignored GitHub properties:
  // - closed_by seems not to be used by webhooks
  // - author_association (not provided by our GitHub API client)
  // - state_reason
  // - reactions
  // - active_lock_reason
  // - [remaining urls]
}
