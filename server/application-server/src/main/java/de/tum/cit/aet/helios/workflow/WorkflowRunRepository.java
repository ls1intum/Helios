package de.tum.cit.aet.helios.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface WorkflowRunRepository
    extends JpaRepository<WorkflowRun, Long>, JpaSpecificationExecutor<WorkflowRun> {
  Optional<WorkflowRun> findById(long id);

  Optional<WorkflowRun> findByIdAndRepositoryRepositoryId(long id, Long repositoryId);

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

  /**
   * Finds the first page of stale workflow runs in non-terminal states and eagerly fetches their
   * repository to avoid lazy-loading issues during reconciliation.
   *
   * @param threshold stale threshold for updatedAt/createdAt
   * @param statuses incomplete statuses to reconcile
   * @param pageable limits the number of rows processed per reconciliation run
   * @return stale workflow runs ordered by oldest first using timestamp+id ordering
   */
  @Query(
      "SELECT wr FROM WorkflowRun wr "
          + "JOIN FETCH wr.repository r "
          + "WHERE wr.status IN :statuses "
          + "AND COALESCE(wr.updatedAt, wr.createdAt) < :threshold "
          + "ORDER BY COALESCE(wr.updatedAt, wr.createdAt) ASC, wr.id ASC")
  List<WorkflowRun> findStaleIncompleteRunsFirstPage(
      @Param("threshold") java.time.OffsetDateTime threshold,
      @Param("statuses") List<WorkflowRun.Status> statuses,
      Pageable pageable);

  /**
   * Finds stale workflow runs in non-terminal states after the given keyset cursor.
   *
   * @param threshold stale threshold for updatedAt/createdAt
   * @param statuses incomplete statuses to reconcile
   * @param cursorTime cursor timestamp of the last processed row (exclusive)
   * @param cursorId cursor id of the last processed row (exclusive tie-breaker)
   * @param pageable limits the number of rows processed per reconciliation run
   * @return stale workflow runs ordered by oldest first using timestamp+id ordering
   */
  @Query(
      "SELECT wr FROM WorkflowRun wr "
          + "JOIN FETCH wr.repository r "
          + "WHERE wr.status IN :statuses "
          + "AND COALESCE(wr.updatedAt, wr.createdAt) < :threshold "
          + "AND ("
          + "  COALESCE(wr.updatedAt, wr.createdAt) > :cursorTime "
          + "  OR (COALESCE(wr.updatedAt, wr.createdAt) = :cursorTime AND wr.id > :cursorId)"
          + ") "
          + "ORDER BY COALESCE(wr.updatedAt, wr.createdAt) ASC, wr.id ASC")
  List<WorkflowRun> findStaleIncompleteRunsAfterCursor(
      @Param("threshold") java.time.OffsetDateTime threshold,
      @Param("statuses") List<WorkflowRun.Status> statuses,
      @Param("cursorTime") java.time.OffsetDateTime cursorTime,
      @Param("cursorId") long cursorId,
      Pageable pageable);

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

  @Query("SELECT pr.id FROM WorkflowRun wr JOIN wr.pullRequests pr WHERE wr.id = :workflowRunId")
  List<Long> findPullRequestIdsByWorkflowRunId(@Param("workflowRunId") Long workflowRunId);

  List<WorkflowRun> findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
      String branch, String headSha, Long repositoryId);

  @Query(
      "SELECT wr FROM WorkflowRun wr "
          + "WHERE wr.workflow.id = :workflowId "
          + "AND wr.repository.repositoryId = :repositoryId "
          + "AND wr.headBranch = :headBranch "
          + "AND wr.headSha = :headSha "
          + "ORDER BY wr.createdAt DESC")
  List<WorkflowRun> findLatestForDeploymentReadiness(
      @Param("workflowId") Long workflowId,
      @Param("repositoryId") Long repositoryId,
      @Param("headBranch") String headBranch,
      @Param("headSha") String headSha,
      Pageable pageable);

  /**
   * IDs of workflow-runs that will **NOT** be deleted by the current policy.
   *
   * <p>Same ranking logic, but we select the rows whose ID is *not* in the
   * `deletable` set.
   */
  @Query(value = """
      WITH ranked AS (
          SELECT id,
                 repository_id,
                 workflow_id,
                 head_branch,
                 created_at,
                 row_number() OVER (
                     PARTITION BY repository_id, workflow_id, head_branch
                     ORDER BY created_at DESC
                 ) AS rn
          FROM workflow_run
          WHERE test_processing_status IS NOT DISTINCT FROM :tps
      ),
      deletable AS (
          SELECT id
          FROM ranked
          WHERE rn > :keep
            AND created_at < now() - (:ageDays * interval '1 day')
      )
      SELECT id
      FROM ranked
      WHERE id NOT IN (SELECT id FROM deletable)
      """, nativeQuery = true)
  List<Long> previewSurvivorRunIds(@Param("keep") int keep,
                                   @Param("ageDays") int ageDays,
                                   @Param("tps") String tps);


  /**
   * IDs of workflow-runs that will be deleted by the current policy.
   *
   * <p>Same ranking logic, but we select the rows whose ID is in the
   * `deletable` set.
   */
  @Query(value = """
      WITH candidates AS (
          SELECT id
          FROM (
              SELECT id,
                     repository_id,
                     workflow_id,
                     head_branch,
                     created_at,
                     row_number() OVER (
                         PARTITION BY repository_id, workflow_id, head_branch
                         ORDER BY created_at DESC
                     ) AS rn
              FROM workflow_run
              WHERE test_processing_status IS NOT DISTINCT FROM :tps
          ) ranked
          WHERE rn > :keep
            AND created_at < now() - (:ageDays * interval '1 day')
      )
      SELECT id FROM candidates
      """, nativeQuery = true)
  List<Long> previewObsoleteRunIds(@Param("keep") int keep,
                                   @Param("ageDays") int ageDays,
                                   @Param("tps") String tps);

  /**
   * Custom database clean-up for workflow runs.
   */
  @Modifying
  @Transactional
  @Query(value = """
      /* ---------------------------------------------------------------
       * Custom database clean-up for workflow runs.
       *
       *   • For every (repository_id, workflow_id, head_branch) combination
       *     keep the newest :keep rows.
       *   • Of the remainder, delete those whose
       *     created_at is at least :ageDays old.
       *
       * All child rows in test_suite → test_case are removed
       * automatically via ON DELETE CASCADE.
       * ------------------------------------------------------------- */
      WITH deletable AS (
          SELECT id
          FROM (
              SELECT id,
                     repository_id,
                     workflow_id,
                     head_branch,
                     created_at,
                     row_number() OVER (
                         PARTITION BY repository_id, workflow_id, head_branch
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

  /**
   * IDs (up to {@code limit}) of workflow runs whose {@code head_branch} no
   * longer exists in the {@code branch} table for the same repository,
   * older than the grace window, and not referenced by any deployment row.
   *
   * <p>Used to preview which runs the orphan-branch sweep would remove in
   * dry-run mode. The matching batched {@code DELETE} query lives in
   * {@link #purgeOrphanBranchRunsBatch(int, int)}.
   *
   * <p>Runs with a {@code NULL head_branch} (tag pushes, scheduled and
   * {@code workflow_dispatch} runs) are excluded — they have no branch
   * identity to compare against and the keep-N policy in
   * {@link #purgeObsoleteRuns} already handles their retention.
   */
  @Query(value = """
      SELECT wr.id
      FROM workflow_run wr
      WHERE wr.created_at < now() - (:graceDays * interval '1 day')
        AND wr.head_branch IS NOT NULL
        AND NOT EXISTS (
          SELECT 1 FROM branch b
          WHERE b.repository_id = wr.repository_id
            AND b.name          = wr.head_branch
        )
        AND NOT EXISTS (
          SELECT 1 FROM helios_deployment hd
          WHERE hd.workflow_run_id = wr.id
        )
        AND NOT EXISTS (
          SELECT 1 FROM deployment d
          WHERE d.workflow_run_id = wr.id
        )
      LIMIT :limit
      """, nativeQuery = true)
  List<Long> previewOrphanBranchRunIds(@Param("graceDays") int graceDays,
                                       @Param("limit") int limit);

  /**
   * Total number of workflow runs the orphan-branch sweep would delete for the
   * given grace window. Uses the exact same predicate as
   * {@link #previewOrphanBranchRunIds(int, int)} and
   * {@link #purgeOrphanBranchRunsBatch(int, int)} but without a {@code LIMIT},
   * so dry-run mode can report the true backlog size rather than a single
   * batch's worth.
   */
  @Query(value = """
      SELECT count(*)
      FROM workflow_run wr
      WHERE wr.created_at < now() - (:graceDays * interval '1 day')
        AND wr.head_branch IS NOT NULL
        AND NOT EXISTS (
          SELECT 1 FROM branch b
          WHERE b.repository_id = wr.repository_id
            AND b.name          = wr.head_branch
        )
        AND NOT EXISTS (
          SELECT 1 FROM helios_deployment hd
          WHERE hd.workflow_run_id = wr.id
        )
        AND NOT EXISTS (
          SELECT 1 FROM deployment d
          WHERE d.workflow_run_id = wr.id
        )
      """, nativeQuery = true)
  long countOrphanBranchRunIds(@Param("graceDays") int graceDays);

  /**
   * Deletes up to {@code batchSize} workflow runs whose {@code head_branch}
   * no longer exists in the {@code branch} table, are older than the grace
   * window, and are not referenced by any deployment. Cascades through
   * {@code test_suite}, {@code test_case}, {@code test_failure_analysis},
   * {@code workflow_run_pull_requests}, and {@code issue_workflow_runs}
   * via existing FK constraints.
   *
   * <p>Bounded by {@code batchSize} so the caller can loop and split the
   * work into multiple short transactions; this avoids long-held locks and
   * unbounded WAL growth on a backlog that may exceed tens of thousands of
   * rows.
   *
   * <p>Note: {@code helios_deployment.workflow_run_id} was intentionally
   * de-FK'd in {@code V42} because that column is populated at dispatch
   * time, before the workflow_run row arrives via webhook sync. The
   * {@code NOT EXISTS} guard here remains correct because the orphan sweep
   * only targets workflow_runs that already exist and are older than the
   * grace window.
   *
   * @return number of {@code workflow_run} rows deleted in this batch (not
   *     including cascaded child rows). Callers loop until the return is
   *     less than {@code batchSize}.
   */
  @Modifying
  @Transactional
  @Query(value = """
      DELETE FROM workflow_run
      WHERE id IN (
        SELECT wr.id
        FROM workflow_run wr
        WHERE wr.created_at < now() - (:graceDays * interval '1 day')
          AND wr.head_branch IS NOT NULL
          AND NOT EXISTS (
            SELECT 1 FROM branch b
            WHERE b.repository_id = wr.repository_id
              AND b.name          = wr.head_branch
          )
          AND NOT EXISTS (
            SELECT 1 FROM helios_deployment hd
            WHERE hd.workflow_run_id = wr.id
          )
          AND NOT EXISTS (
            SELECT 1 FROM deployment d
            WHERE d.workflow_run_id = wr.id
          )
        LIMIT :batchSize
      )
      """, nativeQuery = true)
  int purgeOrphanBranchRunsBatch(@Param("graceDays") int graceDays,
                                 @Param("batchSize") int batchSize);

}
