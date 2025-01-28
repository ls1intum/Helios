package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@DiscriminatorValue(value = "TAG")
@Getter
@Setter
@RequiredArgsConstructor
@IdClass(TagId.class)
@ToString(callSuper = true)
public class Tag {
  @Id
  @ManyToOne
  @JoinColumn(name = "repository_id", nullable = false)
  private GitRepository repository;

  @Id private String name;

  @OneToOne(optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "commit_sha",
        referencedColumnName = "sha",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "repository_id",
        referencedColumnName = "repository_id",
        insertable = false,
        updatable = false)
  })
  private Commit commit;

  @OneToOne
  @JoinColumns({
    @JoinColumn(
        name = "branch_name",
        referencedColumnName = "name",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "repository_id",
        referencedColumnName = "repository_id",
        insertable = false,
        updatable = false)
  })
  private Branch branch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "updated_by_id")
  @ToString.Exclude
  private User createdBy;

  @ManyToMany
  @JoinTable(
      name = "tag_marked_working_by",
      joinColumns = {
        @JoinColumn(name = "tag_name", referencedColumnName = "name"),
        @JoinColumn(name = "repository_id", referencedColumnName = "repository_id")
      },
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @ToString.Exclude
  private Set<User> markedWorkingBy = new HashSet<>();

  @ManyToMany
  @JoinTable(
      name = "tag_marked_broken_by",
      joinColumns = {
        @JoinColumn(name = "tag_name", referencedColumnName = "name"),
        @JoinColumn(name = "repository_id", referencedColumnName = "repository_id")
      },
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @ToString.Exclude
  private Set<User> markedBrokenBy = new HashSet<>();

  @ToString.Exclude private OffsetDateTime createdAt;

  // Explicitly add the fk for the @xTox relations and add custom setters, because the join columns
  // cannot be mixed in respect to insertable and updatable
  @Column(name = "commit_sha", nullable = false)
  private String commitSha;

  @Column(name = "branch_name")
  private String branchName;

  public void setCommit(Commit commit) {
    this.commit = commit;
    this.commitSha = commit.getSha();
  }

  public void setBranch(Branch branch) {
    this.branch = branch;
    this.branchName = branch.getName();
  }

  public int compareToByDate(Tag other) {
    return this.createdAt.compareTo(other.createdAt);
  }
}
