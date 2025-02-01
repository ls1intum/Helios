package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.io.Serializable;
import java.util.Objects;

public class TagId implements Serializable {

  private String name;
  private GitRepository repository;

  // Default constructor
  public TagId() {}

  public TagId(String name, GitRepository repository) {
    this.name = name;
    this.repository = repository;
  }

  // Equals and hashCode
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TagId tagId = (TagId) o;
    return Objects.equals(name, tagId.name) && Objects.equals(repository, tagId.repository);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, repository);
  }
}
