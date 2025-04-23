package de.tum.cit.aet.helios.releaseinfo.releasecandidate;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReleaseCandidateRepository
    extends JpaRepository<ReleaseCandidate, ReleaseCandidateId> {
  List<ReleaseCandidate> findAllByOrderByCreatedAtDesc();

  List<ReleaseCandidate> findByRepository(GitRepository repository);

  Optional<ReleaseCandidate> findByRepositoryRepositoryIdAndName(Long repositoryId, String name);

  Optional<ReleaseCandidate> findByRepositoryRepositoryIdAndReleaseId(
      Long repositoryId, Long releaseId);

  List<ReleaseCandidate> findByRepositoryRepositoryIdAndCommitSha(
      Long repositoryId, String commitSha);

  boolean existsByRepositoryRepositoryIdAndName(Long repositoryId, String name);

  Optional<ReleaseCandidate> deleteByRepositoryRepositoryIdAndName(Long repositoryId, String name);
}
