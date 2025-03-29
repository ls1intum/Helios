package de.tum.cit.aet.helios.releaseinfo.releasecandidate;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.releaseinfo.release.Release;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
@IdClass(ReleaseCandidateId.class)
@ToString(callSuper = true)
@Filter(name = "gitRepositoryFilter")
public class ReleaseCandidate {
  @Id
  @ManyToOne
  @JoinColumn(name = "repository_id", nullable = false)
  private GitRepository repository;

  @Id private String name;

  @ManyToOne(optional = false)
  private Commit commit;

  @ManyToOne(fetch = FetchType.LAZY)
  private Branch branch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_id")
  private User createdBy;

  @OneToMany(mappedBy = "releaseCandidate", cascade = CascadeType.ALL)
  @ToString.Exclude
  private Set<ReleaseCandidateEvaluation> evaluations = new HashSet<>();

  private OffsetDateTime createdAt;

  @OneToOne private Release release;

  public int compareToByDate(ReleaseCandidate other) {
    return this.createdAt.compareTo(other.createdAt);
  }
}
