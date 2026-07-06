package de.tum.cit.aet.helios.deployment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
  List<Deployment> findByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);

  @SuppressWarnings("checkstyle:MethodName")
  List<Deployment> findByPullRequest_IdOrderByCreatedAtDesc(Long pullRequestId);

  List<Deployment> findByRepositoryRepositoryIdAndSha(Long repositoryId, String sha);

  // Explicit per-repository scoped finders (Option B).
  List<Deployment> findByRepositoryRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

  Optional<Deployment> findByIdAndRepositoryRepositoryId(Long id, Long repositoryId);

  List<Deployment> findByRepositoryRepositoryIdAndRefOrderByCreatedAtDesc(
      Long repositoryId, String ref);

  Optional<Deployment> findFirstByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);

  /**
   * Finds the first page of stale deployments in incomplete states (e.g., IN_PROGRESS, QUEUED).
   *
   * @param threshold stale threshold for updatedAt/createdAt
   * @param states incomplete states to reconcile
   * @param pageable limits the number of rows processed per reconciliation run
   * @return stale deployments ordered by oldest first using timestamp+id ordering
   */
  @Query(
      "SELECT d FROM Deployment d "
          + "JOIN FETCH d.repository r "
          + "WHERE d.state IN :states "
          + "AND COALESCE(d.updatedAt, d.createdAt) < :threshold "
          + "ORDER BY COALESCE(d.updatedAt, d.createdAt) ASC, d.id ASC")
  List<Deployment> findStaleIncompleteDeploymentsFirstPage(
      @Param("threshold") OffsetDateTime threshold,
      @Param("states") List<Deployment.State> states,
      Pageable pageable);

  /**
   * Finds stale deployments in incomplete states after the given keyset cursor.
   *
   * @param threshold stale threshold for updatedAt/createdAt
   * @param states incomplete states to reconcile
   * @param cursorTime cursor timestamp of the last processed row (exclusive)
   * @param cursorId cursor id of the last processed row (exclusive tie-breaker)
   * @param pageable limits the number of rows processed per reconciliation run
   * @return stale deployments ordered by oldest first using timestamp+id ordering
   */
  @Query(
      "SELECT d FROM Deployment d "
          + "JOIN FETCH d.repository r "
          + "WHERE d.state IN :states "
          + "AND COALESCE(d.updatedAt, d.createdAt) < :threshold "
          + "AND ("
          + "  COALESCE(d.updatedAt, d.createdAt) > :cursorTime "
          + "  OR (COALESCE(d.updatedAt, d.createdAt) = :cursorTime AND d.id > :cursorId)"
          + ") "
          + "ORDER BY COALESCE(d.updatedAt, d.createdAt) ASC, d.id ASC")
  List<Deployment> findStaleIncompleteDeploymentsAfterCursor(
      @Param("threshold") OffsetDateTime threshold,
      @Param("states") List<Deployment.State> states,
      @Param("cursorTime") OffsetDateTime cursorTime,
      @Param("cursorId") long cursorId,
      Pageable pageable);
}
