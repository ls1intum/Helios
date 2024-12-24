package de.tum.cit.aet.helios.github;

import de.tum.cit.aet.helios.filters.RepositoryFilterEntity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public abstract class BaseGitServiceEntity extends RepositoryFilterEntity {
  @Id protected Long id;

  protected OffsetDateTime createdAt;

  protected OffsetDateTime updatedAt;
}
