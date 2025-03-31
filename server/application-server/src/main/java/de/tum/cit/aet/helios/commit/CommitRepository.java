package de.tum.cit.aet.helios.commit;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitRepository extends JpaRepository<Commit, CommitId> {
  Optional<Commit> findByShaAndRepositoryRepositoryId(String sha, Long repositoryId);

  List<Commit> findByRepositoryRepositoryId(Long repositoryId);

  void deleteByShaAndRepositoryRepositoryId(String sha, Long repositoryId);

  Optional<Commit> findByShaAndRepository(String commitSha, GitRepository repository);
}
