package de.tum.cit.aet.helios.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Long> {
  Optional<WorkflowRun> findById(long id);

  @Query(
      "SELECT DISTINCT wr FROM WorkflowRun wr "
          + "JOIN wr.pullRequests pr "
          + "WHERE pr.id = :pullRequestId "
          + "AND wr.headSha = :headSha")
  @EntityGraph(attributePaths = {"testSuites"})
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
          + "WHERE wr.headBranch = :branch "
          + "AND wr.headSha = :headSha "
          + "AND wr.repository.repositoryId = :repositoryId "
          + "ORDER BY wr.createdAt DESC")
  @EntityGraph(attributePaths = {"testSuites"})
  List<WorkflowRun> findByHeadBranchAndHeadShaAndRepositoryIdWithTestSuites(
      String branch, String headSha, Long repositoryId);

  List<WorkflowRun> findByPullRequestsIdAndHeadSha(Long pullRequestsId, String headSha);

  List<WorkflowRun> findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
      String branch, String headSha, Long repositoryId);

  /**
   * Custom database clean‑up for workflow runs.
   */
  @Modifying
  @Transactional
  @Query(value = """
      /* ---------------------------------------------------------------
       * Delete workflow runs that match the caller‑supplied policy:
       *
       *   • For every (repository_id, head_branch) partition,
       *     keep the newest :keep rows.
       *   • Of the remainder, delete those whose
       *     test_processing_status matches :tps
       *     (or any value if :tps is NULL)
       *     AND whose created_at is at least :ageDays old.
       *
       * All child rows in test_suite → test_case are removed
       * automatically via ON DELETE CASCADE.
       * ------------------------------------------------------------- */
      WITH deletable AS (
          SELECT id
          FROM (
              SELECT id,
                     repository_id,
                     head_branch,
                     created_at,
                     row_number() OVER (
                         PARTITION BY repository_id, head_branch
                         ORDER BY created_at DESC
                     ) AS rn
              FROM workflow_run
              WHERE test_processing_status IS NOT DISTINCT FROM :tps
          ) ranked
          WHERE rn > :keep
            AND created_at < now() - (:ageDays * interval '1 day')
      )
      DELETE FROM workflow_run wr
      USING  deletable d
      WHERE  wr.id = d.id
      """, nativeQuery = true)
  int purgeObsoleteRuns(@Param("keep") int keepPerCombo,
                        @Param("ageDays") int ageDays,
                        @Param("tps") String testProcessingStatus);

}
