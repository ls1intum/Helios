package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.issue.Issue;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

  @Query(
      """
      SELECT p
      FROM PullRequest p
      WHERE p.repository.repositoryId = :id
        AND (p.headRefName = :ref OR p.headSha = :sha)
        AND p.state = 'OPEN'
      ORDER BY p.updatedAt DESC
      LIMIT 1
      """)
  Optional<PullRequest> findOpenPrByBranchNameOrSha(
      @Param("id") Long id, @Param("ref") String ref, @Param("sha") String sha);

  Optional<PullRequest> findByRepositoryRepositoryIdAndHeadRefName(Long id, String ref);

  Optional<PullRequest> findByRepositoryRepositoryIdAndHeadRefNameAndState(
      Long repositoryId, String headRefName, Issue.State state);

  List<PullRequest> findAllByState(Issue.State state);

  List<PullRequest> findAllByOrderByUpdatedAtDesc();

  List<PullRequest> findByRepositoryRepositoryIdOrderByUpdatedAtDesc(Long repositoryId);

  Optional<PullRequest> findByRepositoryRepositoryIdAndNumber(Long repositoryId, Integer number);
}
