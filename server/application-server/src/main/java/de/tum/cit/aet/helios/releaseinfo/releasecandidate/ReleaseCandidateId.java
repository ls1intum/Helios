package de.tum.cit.aet.helios.releaseinfo.releasecandidate;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.io.Serializable;
import java.util.Objects;

public class ReleaseCandidateId implements Serializable {

  private String name;
  private GitRepository repository;

  // Default constructor
  public ReleaseCandidateId() {}

  public ReleaseCandidateId(String name, GitRepository repository) {
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
    ReleaseCandidateId releaseCandidateId = (ReleaseCandidateId) o;
    return Objects.equals(name, releaseCandidateId.name)
        && Objects.equals(repository, releaseCandidateId.repository);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, repository);
  }
}
