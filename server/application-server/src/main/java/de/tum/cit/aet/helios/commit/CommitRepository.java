package de.tum.cit.aet.helios.commit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitRepository extends JpaRepository<Commit, CommitId> {
  Optional<Commit> findByShaAndRepositoryRepositoryId(String sha, Long repositoryId);

  List<Commit> findByRepositoryRepositoryId(Long repositoryId);

  void deleteByShaAndRepositoryRepositoryId(String sha, Long repositoryId);
}
