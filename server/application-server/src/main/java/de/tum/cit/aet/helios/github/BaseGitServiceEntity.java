package de.tum.cit.aet.helios.github;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Data
@FilterDef(
    name = "gitRepositoryFilter",
    parameters = @ParamDef(name = "repository_id", type = Long.class),
    defaultCondition = "repository_id = :repository_id")
@Filter(name = "gitRepositoryFilter")
public abstract class BaseGitServiceEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_id", nullable = false)
  private GitRepository repository;

  @Id protected Long id;

  protected OffsetDateTime createdAt;

  protected OffsetDateTime updatedAt;
}
