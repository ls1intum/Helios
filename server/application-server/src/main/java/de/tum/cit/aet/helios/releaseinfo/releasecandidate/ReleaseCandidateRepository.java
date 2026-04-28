package de.tum.cit.aet.helios.releaseinfo.releasecandidate;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReleaseCandidateRepository extends JpaRepository<ReleaseCandidate, Long> {
  List<ReleaseCandidate> findAllByOrderByCreatedAtDesc();

  List<ReleaseCandidate> findByRepository(GitRepository repository);

  Optional<ReleaseCandidate> findByRepositoryRepositoryIdAndName(Long repositoryId, String name);

  Optional<ReleaseCandidate> findByRepositoryRepositoryIdAndReleaseId(
      Long repositoryId, Long releaseId);

  List<ReleaseCandidate> findByRepositoryRepositoryIdAndCommitSha(
      Long repositoryId, String commitSha);

  @Query(
      "SELECT rc FROM ReleaseCandidate rc "
          + "JOIN FETCH rc.repository r "
          + "JOIN FETCH rc.commit c "
          + "WHERE r.repositoryId IN :repositoryIds "
          + "AND c.sha IN :commitShas")
  List<ReleaseCandidate> findByRepositoryIdsAndCommitShas(
      @Param("repositoryIds") List<Long> repositoryIds, @Param("commitShas") List<String> commitShas);

  boolean existsByRepositoryRepositoryIdAndName(Long repositoryId, String name);

  Optional<ReleaseCandidate> deleteByRepositoryRepositoryIdAndName(Long repositoryId, String name);
}
