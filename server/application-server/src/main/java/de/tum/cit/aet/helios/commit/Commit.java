package de.tum.cit.aet.helios.commit;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@IdClass(CommitId.class)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Commit {
  @Id private String sha;

  @Id
  @ManyToOne
  @JoinColumn(name = "repository_id", nullable = false)
  @ToString.Exclude
  private GitRepository repository;

  @Column(length = 1023)
  private String message;

  private OffsetDateTime authoredAt;

  @ManyToOne
  @JoinColumn(name = "author_id")
  @ToString.Exclude
  private User author;
}
