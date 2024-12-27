package de.tum.cit.aet.helios.commit;

import java.util.Objects;

public class CommitId {
  private String sha;
  private Long repository;

  // Default constructor
  public CommitId() {}

  public CommitId(String sha, Long repository) {
    this.sha = sha;
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
    CommitId commitId = (CommitId) o;
    return Objects.equals(sha, commitId.sha) && Objects.equals(repository, commitId.repository);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sha, repository);
  }
}
