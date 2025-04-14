package de.tum.cit.aet.helios.releaseinfo.releasecandidate;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ReleaseCandidateRepository extends JpaRepository<ReleaseCandidate, Long> {
  List<ReleaseCandidate> findAllByOrderByCreatedAtDesc();

  List<ReleaseCandidate> findByRepository(GitRepository repository);

  Optional<ReleaseCandidate> findByRepositoryRepositoryIdAndName(Long repositoryId, String name);

  Optional<ReleaseCandidate> findByRepositoryRepositoryIdAndReleaseId(
      Long repositoryId, Long releaseId);

  List<ReleaseCandidate> findByRepositoryRepositoryIdAndCommitSha(
      Long repositoryId, String commitSha);

  boolean existsByRepositoryRepositoryIdAndName(Long repositoryId, String name);

  @Transactional
  @Modifying
  @Query("DELETE FROM ReleaseCandidate rc WHERE rc.repository.repositoryId = ?1 AND rc.name = ?2")
  void deleteByRepositoryRepositoryIdAndName(Long repositoryId, String name);

  @Transactional
  default Optional<ReleaseCandidate> deleteAndReturnByRepositoryRepositoryIdAndName(
      Long repositoryId, String name) {
    Optional<ReleaseCandidate> candidate = findByRepositoryRepositoryIdAndName(repositoryId, name);
    candidate.ifPresent(this::delete);
    return candidate;
  }
}
