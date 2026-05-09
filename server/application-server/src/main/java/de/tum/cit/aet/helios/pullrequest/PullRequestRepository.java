package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.label.Label;
import de.tum.cit.aet.helios.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long>,
    JpaSpecificationExecutor<PullRequest> {

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

  List<PullRequest> findByRepositoryRepositoryIdAndStateOrderByUpdatedAtDesc(
      Long repositoryId, Issue.State state);

  List<PullRequest> findAllByOrderByUpdatedAtDesc();

  List<PullRequest> findByRepositoryRepositoryIdOrderByUpdatedAtDesc(Long repositoryId);

  Optional<PullRequest> findByRepositoryRepositoryIdAndNumber(Long repositoryId, Integer number);

  @Query(
      """
      SELECT DISTINCT p.author
      FROM PullRequest p
      WHERE p.repository.repositoryId = :repositoryId
        AND p.author IS NOT NULL
      """)
  List<User> findDistinctAuthorsByRepositoryId(@Param("repositoryId") Long repositoryId);

  @Query(
      """
      SELECT DISTINCT assignee
      FROM PullRequest p
      JOIN p.assignees assignee
      WHERE p.repository.repositoryId = :repositoryId
      """)
  List<User> findDistinctAssigneesByRepositoryId(@Param("repositoryId") Long repositoryId);

  @Query(
      """
      SELECT DISTINCT reviewer
      FROM PullRequest p
      JOIN p.requestedReviewers reviewer
      WHERE p.repository.repositoryId = :repositoryId
      """)
  List<User> findDistinctReviewersByRepositoryId(@Param("repositoryId") Long repositoryId);

  @Query(
      """
      SELECT DISTINCT label
      FROM PullRequest p
      JOIN p.labels label
      WHERE p.repository.repositoryId = :repositoryId
      """)
  List<Label> findDistinctLabelsByRepositoryId(@Param("repositoryId") Long repositoryId);

}
