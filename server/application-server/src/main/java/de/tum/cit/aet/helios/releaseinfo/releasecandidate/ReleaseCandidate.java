package de.tum.cit.aet.helios.releaseinfo.releasecandidate;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.releaseinfo.release.Release;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Filter;

@Entity
@Getter
@Setter
@ToString(callSuper = true)
@Table(
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"repository_id", "name"}),
      @UniqueConstraint(columnNames = {"release_id"})
    })
@Filter(name = "gitRepositoryFilter")
public class ReleaseCandidate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "repository_id", nullable = false)
  private GitRepository repository;

  @Column(nullable = false)
  private String name;

  @ManyToOne(optional = false)
  @JoinColumns({
    @JoinColumn(name = "commit_repository_id", referencedColumnName = "repository_id"),
    @JoinColumn(name = "commit_sha", referencedColumnName = "sha")
  })
  private Commit commit;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumns({
    @JoinColumn(name = "branch_repository_id", referencedColumnName = "repository_id"),
    @JoinColumn(name = "branch_name", referencedColumnName = "name")
  })
  private Branch branch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_id")
  private User createdBy;

  @OneToMany(mappedBy = "releaseCandidate", cascade = CascadeType.ALL)
  @ToString.Exclude
  private Set<ReleaseCandidateEvaluation> evaluations = new HashSet<>();

  private OffsetDateTime createdAt;

  @OneToOne
  @JoinColumn(name = "release_id")
  private Release release;

  @Column(columnDefinition = "TEXT")
  private String body;

  public int compareToByDate(ReleaseCandidate other) {
    return this.createdAt.compareTo(other.createdAt);
  }
}
