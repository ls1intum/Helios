package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
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

@Entity
@Getter
@Setter
@IdClass(TagId.class)
@ToString(callSuper = true)
public class Tag {
  @Id
  @ManyToOne
  @JoinColumn(name = "repository_id", nullable = false)
  private GitRepository repository;

  @Id private String name;

  @OneToOne(optional = false, cascade = CascadeType.ALL)
  private Commit commit;

  @ManyToOne(fetch = FetchType.LAZY)
  private Branch branch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_id")
  private User createdBy;

  @OneToMany(mappedBy = "tag", cascade = CascadeType.ALL)
  @ToString.Exclude
  private Set<TagEvaluation> evaluations = new HashSet<>();

  private OffsetDateTime createdAt;

  public int compareToByDate(Tag other) {
    return this.createdAt.compareTo(other.createdAt);
  }
}
