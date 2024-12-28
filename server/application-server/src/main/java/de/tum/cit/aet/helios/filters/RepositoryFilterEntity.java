package de.tum.cit.aet.helios.filters;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.Filter;

@MappedSuperclass
@Data
@Filter(name = "gitRepositoryFilter")
public abstract class RepositoryFilterEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_id")
  @ToString.Exclude
  protected GitRepository repository;
}
