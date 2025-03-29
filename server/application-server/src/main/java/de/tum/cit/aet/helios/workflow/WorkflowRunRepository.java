package de.tum.cit.aet.helios.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Long> {
  Optional<WorkflowRun> findById(long id);

  // This loads the test suites for the workflow run eagerly
  @Query(
      "SELECT DISTINCT wr FROM WorkflowRun wr "
          + "JOIN wr.pullRequests pr "
          + "LEFT JOIN FETCH wr.testSuites "
          + "WHERE pr.id = :pullRequestId "
          + "AND wr.headSha = :headSha")
  List<WorkflowRun> findByPullRequestsIdAndHeadShaWithTestSuites(
      Long pullRequestId, String headSha);

  /**
   * Returns the n-th latest commit SHA from the commit history for a given pull request, excluding
   * the specified head commit. The commit history is purely based on workflow runs. Potentially, a
   * workflow run for an old commit could be re-run, so it's not guaranteed that this method
   * accurately reflects the commit history.
   *
   * @param prId the ID of the pull request for which the commit history is queried
   * @param offset the offset in the commit list (0 for the first commit behind the head, 1 for the
   *     second, etc.)
   * @param headCommit the commit SHA to exclude from the results (the head commit)
   * @return an Optional containing the n-th latest commit SHA behind the head commit, or empty if
   *     such commit does not exist
   */
  @Query(
      nativeQuery = true,
      value =
          """
          WITH commit_history AS (
              SELECT
                  wr.head_sha,
                  MAX(wr.created_at) AS latest_run
              FROM workflow_run wr
              JOIN workflow_run_pull_requests wrpr ON wr.id = wrpr.workflow_run_id
              WHERE wrpr.pull_requests_id = :prId
              GROUP BY wr.head_sha
              ORDER BY latest_run DESC
          )
          SELECT head_sha
          FROM commit_history
          WHERE head_sha != :head
          OFFSET :offset
          LIMIT 1
          """)
  Optional<String> findNthLatestCommitShaBehindHeadByPullRequestId(
      @Param("prId") Long prId, @Param("offset") int offset, @Param("head") String headCommit);

  /**
   * Returns the n-th latest commit SHA from the commit history for a given pull request, excluding
   * the specified head commit. The commit history is purely based on workflow runs. Potentially, a
   * workflow run for an old commit could be re-run, so it's not guaranteed that this method
   * accurately reflects the commit history.
   *
   * @param headBranch the branch for which the commit history is queried
   * @param repoId the ID of the repository for which the commit history is queried
   * @param offset the offset in the commit list (0 for the first commit behind the head, 1 for the
   *     second, etc.)
   * @param headCommit the commit SHA to exclude from the results (the head commit)
   * @return an Optional containing the n-th latest commit SHA behind the head commit, or empty if
   *     such commit does not exist
   */
  @Query(
      nativeQuery = true,
      value =
          """
          WITH commit_history AS (
              SELECT
                  wr.head_sha,
                  MAX(wr.created_at) AS latest_run
              FROM workflow_run wr
              WHERE wr.head_branch = :headBranch
              AND wr.repository_id = :repoId
              AND NOT EXISTS (
                  SELECT 1
                  FROM workflow_run_pull_requests wrpr
                  WHERE wr.id = wrpr.workflow_run_id
              )
              GROUP BY wr.head_sha
              ORDER BY latest_run DESC
          )
          SELECT head_sha
          FROM commit_history
          WHERE head_sha != :head
          OFFSET :offset
          LIMIT 1
          """)
  Optional<String> findNthLatestCommitShaBehindHeadByBranchAndRepoId(
      @Param("headBranch") String headBranch,
      @Param("repoId") Long repoId,
      @Param("offset") int offset,
      @Param("head") String headCommit);

  @Query(
      "SELECT DISTINCT wr FROM WorkflowRun wr "
          + "LEFT JOIN FETCH wr.testSuites "
          + "WHERE wr.headBranch = :branch "
          + "AND wr.headSha = :headSha "
          + "AND wr.repository.repositoryId = :repositoryId "
          + "AND wr.pullRequests IS EMPTY")
  List<WorkflowRun> findByHeadBranchAndHeadShaAndRepositoryIdAndPullRequestsIsNullWithTestSuites(
      String branch, String headSha, Long repositoryId);

  @Query(
      value =
          """
          SELECT DISTINCT wr FROM WorkflowRun wr
          LEFT JOIN FETCH wr.testSuites
          WHERE wr.headBranch = :branch
          AND wr.repository.repositoryId = :repositoryId
          AND wr.pullRequests IS EMPTY
          ORDER BY wr.createdAt DESC
          LIMIT 1
          """)
  List<WorkflowRun> findByHeadBranchAndRepositoryIdAndPullRequestsIsNullWithTestSuites(
      @Param("branch") String branch, @Param("repositoryId") Long repositoryId);

  List<WorkflowRun> findByPullRequestsIdAndHeadSha(Long pullRequestsId, String headSha);

  List<WorkflowRun> findByHeadBranchAndHeadShaAndRepositoryRepositoryIdAndPullRequestsIsNull(
      String branch, String headSha, Long repositoryId);
}
