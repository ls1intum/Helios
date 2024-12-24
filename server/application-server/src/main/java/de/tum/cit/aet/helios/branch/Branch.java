package de.tum.cit.aet.helios.branch;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
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
import org.hibernate.annotations.Filter;

@Entity
@IdClass(BranchId.class)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@Filter(name = "gitRepositoryFilter")
public class Branch {

  @Id private String name;

  @Id
  @ManyToOne
  @JoinColumn(name = "repository_id", nullable = false)
  @ToString.Exclude
  private GitRepository repository;

  private String commitSha;

  @JsonProperty("protected")
  private boolean protection;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;
}
