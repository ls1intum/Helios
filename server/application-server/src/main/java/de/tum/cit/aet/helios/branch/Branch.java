package de.tum.cit.aet.helios.branch;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@IdClass(BranchId.class)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Branch {

  @Id private String name;

  @Id
  @ManyToOne
  @JoinColumn(name = "repository_id", nullable = false)
  @ToString.Exclude
  private GitRepository repository;

  private String commit_sha;

  @JsonProperty("protected")
  private boolean protection;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;
}
