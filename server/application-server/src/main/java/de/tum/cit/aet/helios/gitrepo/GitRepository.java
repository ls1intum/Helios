package de.tum.cit.aet.helios.gitrepo;

import de.tum.cit.aet.helios.label.Label;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Filter;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "repository")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@Filter(name = "gitRepositoryFilter")
public class GitRepository {

  @Id
  @Column(name = "repository_id")
  protected Long repositoryId;

  protected OffsetDateTime createdAt;

  protected OffsetDateTime updatedAt;

  @NonNull private String name;

  @NonNull private String nameWithOwner;

  // Whether the repository is private or public.
  private boolean isPrivate;

  @NonNull private String htmlUrl;

  private String description;

  private String homepage;

  @NonNull private OffsetDateTime pushedAt;

  private boolean isArchived;

  // Returns whether this repository disabled.
  private boolean isDisabled;

  @NonNull
  @Enumerated(EnumType.STRING)
  private Visibility visibility;

  private int stargazersCount;

  private int watchersCount;

  private int pullRequestCount;

  private int branchCount;

  private int environmentCount;

  private String latestReleaseTagName;

  @ManyToMany
  @JoinTable(
      name = "repository_contributor",
      joinColumns = @JoinColumn(name = "contributor_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @ToString.Exclude
  private Set<User> contributors = new HashSet<>();

  @NonNull private String defaultBranch;

  private boolean hasIssues;

  private boolean hasProjects;

  private boolean hasWiki;

  @OneToMany(mappedBy = "repository", cascade = CascadeType.REMOVE, orphanRemoval = true)
  @ToString.Exclude
  private Set<Label> labels = new HashSet<>();

  public enum Visibility {
    PUBLIC,
    PRIVATE,
    INTERNAL,
    UNKNOWN
  }

  // Missing properties:
  // Issue, Milestone
  // owner
  // organization

  // Ignored GitHub properties:
  // - subscribersCount
  // - hasPages
  // - hasDownloads
  // - hasDiscussions
  // - topics
  // - size
  // - fork
  // - forks_count
  // - default_branch
  // - open_issues_count (cached number)
  // - is_template
  // - permissions
  // - allow_rebase_merge
  // - template_repository
  // - allow_squash_merge
  // - allow_auto_merge
  // - delete_branch_on_merge
  // - allow_merge_commit
  // - allow_forking
  // - network_count
  // - license
  // - parent
  // - source
}
