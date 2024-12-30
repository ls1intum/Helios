package de.tum.cit.aet.helios.commit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitRepository extends JpaRepository<Commit, CommitId> {
  Optional<Commit> findByShaAndRepositoryId(String sha, Long repositoryId);

  List<Commit> findByRepositoryId(Long repositoryId);

  void deleteByShaAndRepositoryId(String sha, Long repositoryId);
}
