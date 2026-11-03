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
   * the specified head commit. The commit history is derived purely from workflow runs (ordered by
   * each commit's most recent run, with the SHA as a deterministic tiebreaker). A fresh run for an
   * old commit (e.g. {@code workflow_dispatch} or a scheduled run) can therefore reorder history; a
   * plain re-run cannot, since it keeps the original run's {@code created_at}.
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
              )
              SELECT head_sha
              FROM commit_history
              WHERE head_sha != :head
              ORDER BY latest_run DESC, head_sha DESC
              OFFSET :offset
              LIMIT 1
              """)
  Optional<String> findNthLatestCommitShaBehindHeadByPullRequestId(
      @Param("prId") Long prId, @Param("offset") int offset, @Param("head") String headCommit);

  /**
   * Returns the n-th latest commit SHA from the commit history for a given pull request, excluding
   * the specified head commit. The commit history is derived purely from workflow runs (ordered by
   * each commit's most recent run, with the SHA as a deterministic tiebreaker). A fresh run for an
   * old commit (e.g. {@code workflow_dispatch} or a scheduled run) can therefore reorder history; a
   * plain re-run cannot, since it keeps the original run's {@code created_at}.
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
              )
              SELECT head_sha
              FROM commit_history
              WHERE head_sha != :head
              ORDER BY latest_run DESC, head_sha DESC
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
          SELECT wr.id,
                 wr.repository_id,
                 wr.workflow_id,
                 wr.head_branch,
                 wr.created_at,
                 (wr.head_branch = gr.default_branch) IS TRUE AS is_default_branch,
                 row_number() OVER (
                     PARTITION BY wr.repository_id, wr.workflow_id, wr.head_branch
                     ORDER BY wr.created_at DESC
                 ) AS rn
          FROM workflow_run wr
          LEFT JOIN repository gr ON gr.repository_id = wr.repository_id
          WHERE wr.test_processing_status IS NOT DISTINCT FROM :tps
      ),
      deletable AS (
          SELECT id
          FROM ranked
          WHERE rn > :keep
            AND NOT is_default_branch
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
              SELECT wr.id,
                     wr.repository_id,
                     wr.workflow_id,
                     wr.head_branch,
                     wr.created_at,
                     (wr.head_branch = gr.default_branch) IS TRUE AS is_default_branch,
                     row_number() OVER (
                         PARTITION BY wr.repository_id, wr.workflow_id, wr.head_branch
                         ORDER BY wr.created_at DESC
                     ) AS rn
              FROM workflow_run wr
              LEFT JOIN repository gr ON gr.repository_id = wr.repository_id
              WHERE wr.test_processing_status IS NOT DISTINCT FROM :tps
          ) ranked
          WHERE rn > :keep
            AND NOT is_default_branch
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
       *   • Runs on the default branch of a repository are never touched by
       *     keep-N pruning — only the hard max-age cap removes them. The
       *     exemption applies only when head_branch equals a non-null
       *     default_branch ((=) IS TRUE), so NULL branches and repos without
       *     a default branch stay prunable. It lives in the deletable filter,
       *     not the ranked scope, so the survivor preview still lists
       *     default-branch runs. Keep this CTE in sync with the two preview
       *     queries above.
       *
       * All child rows in test_suite → test_case are removed
       * automatically via ON DELETE CASCADE.
       * ------------------------------------------------------------- */
      WITH deletable AS (
          SELECT id
          FROM (
              SELECT wr.id,
                     wr.repository_id,
                     wr.workflow_id,
                     wr.head_branch,
                     wr.created_at,
                     (wr.head_branch = gr.default_branch) IS TRUE AS is_default_branch,
                     row_number() OVER (
                         PARTITION BY wr.repository_id, wr.workflow_id, wr.head_branch
                         ORDER BY wr.created_at DESC
                     ) AS rn
              FROM workflow_run wr
              LEFT JOIN repository gr ON gr.repository_id = wr.repository_id
              WHERE wr.test_processing_status IS NOT DISTINCT FROM :tps
          ) ranked
          WHERE rn > :keep
            AND NOT is_default_branch
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
   * Number of workflow runs the max-age cap would delete — dry-run preview of
   * {@link #purgeRunsOlderThan(int, int)}. Same predicate as the delete: older
   * than {@code maxAgeDays} and not referenced by a deployment.
   */
  @Query(value = """
      SELECT count(*)
      FROM workflow_run wr
      WHERE wr.created_at < now() - (:maxAgeDays * interval '1 day')
        AND NOT EXISTS (
          SELECT 1 FROM helios_deployment hd
          WHERE hd.workflow_run_id = wr.id
        )
        AND NOT EXISTS (
          SELECT 1 FROM deployment d
          WHERE d.workflow_run_id = wr.id
        )
      """, nativeQuery = true)
  long countRunsOlderThan(@Param("maxAgeDays") int maxAgeDays);

  /**
   * Hard retention cap: deletes workflow runs older than {@code maxAgeDays},
   * regardless of status, branch (default branches included) or any keep-N
   * policy. Runs still referenced by a {@code helios_deployment} or
   * {@code deployment} row are preserved — same contract as the orphan-branch
   * sweep — so the deployment → build link never dangles. Child rows in
   * test_suite → test_case, workflow_job and workflow_run_pull_requests
   * cascade via their FKs.
   *
   * <p>Deletes at most {@code batchSize} runs per call, each call its own
   * transaction; the caller loops until a call deletes fewer than
   * {@code batchSize}. This bounds lock-hold time and WAL growth on large
   * backlogs where the cascade touches millions of {@code test_case} rows.
   */
  @Modifying
  @Transactional
  @Query(value = """
      DELETE FROM workflow_run
      WHERE id IN (
          SELECT wr.id
          FROM workflow_run wr
          WHERE wr.created_at < now() - (:maxAgeDays * interval '1 day')
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
  int purgeRunsOlderThan(@Param("maxAgeDays") int maxAgeDays,
                         @Param("batchSize") int batchSize);

  /**
   * Distinct repository ids that currently have at least one orphan-branch
   * candidate (see {@link #findOrphanBranchRunCandidatesForRepo}). The caller
   * iterates these so it can fetch each repo's live branch set from GitHub once
   * and confirm candidates against it.
   *
   * <p>Runs with a {@code NULL repository_id} are excluded — their branch can't
   * be confirmed against any GitHub repo, so they are never swept.
   */
  @Query(value = """
      SELECT DISTINCT wr.repository_id
      FROM workflow_run wr
      WHERE wr.created_at < now() - (:graceDays * interval '1 day')
        AND wr.head_branch IS NOT NULL
        AND wr.repository_id IS NOT NULL
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
  List<Long> findRepositoriesWithOrphanCandidates(@Param("graceDays") int graceDays);

  /**
   * Candidate orphan-branch runs (up to {@code limit}) for a single repository:
   * runs whose {@code head_branch} is absent from the {@code branch} table,
   * older than the grace window, and not referenced by a {@code deployment} or
   * {@code helios_deployment} row.
   *
   * <p>These are <em>candidates</em>, not confirmed deletions. The caller
   * re-checks each candidate's {@code headBranch} against GitHub's live branch
   * list before deleting, so a branch that merely went missing from our local
   * {@code branch} table (a sync gap) is healed via a targeted re-sync instead
   * of being swept.
   *
   * <p>Runs with a {@code NULL head_branch} are excluded — they have no branch
   * identity; the keep-N policy in {@link #purgeObsoleteRuns} handles them.
   */
  @Query(value = """
      SELECT wr.id          AS "id",
             wr.head_branch AS "headBranch"
      FROM workflow_run wr
      WHERE wr.repository_id = :repositoryId
        AND wr.created_at < now() - (:graceDays * interval '1 day')
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
  List<OrphanBranchRunCandidate> findOrphanBranchRunCandidatesForRepo(
      @Param("repositoryId") long repositoryId,
      @Param("graceDays") int graceDays,
      @Param("limit") int limit);

  /**
   * Total number of workflow runs the orphan-branch sweep would consider for the
   * given grace window, <em>before</em> the per-run GitHub confirmation. Same
   * predicate as {@link #findOrphanBranchRunCandidatesForRepo} (minus the
   * per-repo filter / limit), so dry-run mode can log the backlog size.
   */
  @Query(value = """
      SELECT count(*)
      FROM workflow_run wr
      WHERE wr.created_at < now() - (:graceDays * interval '1 day')
        AND wr.head_branch IS NOT NULL
        AND wr.repository_id IS NOT NULL
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
   * Lightweight projection of an orphan-branch sweep candidate: the run id (to
   * delete by) and its head branch (to confirm against GitHub).
   */
  interface OrphanBranchRunCandidate {
    Long getId();

    String getHeadBranch();
  }
}
