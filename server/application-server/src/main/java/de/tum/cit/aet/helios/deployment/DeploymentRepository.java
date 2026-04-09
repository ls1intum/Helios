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

  List<Deployment> findByRepositoryRepositoryIdAndRefOrderByCreatedAtDesc(
      Long repositoryId, String ref);

  Optional<Deployment> findFirstByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);

  /**
   * Finds stale deployments in incomplete states (e.g., IN_PROGRESS, QUEUED) that have not been
   * updated before the specified threshold. This method is used for reconciliation to identify
   * deployments that may be stuck and need to be re-synced with GitHub.
   *
   * @param threshold stale threshold for updatedAt/createdAt
   * @param states incomplete states to reconcile
   * @param pageable limits the number of rows processed per reconciliation run
   * @return stale deployments ordered by oldest first
   */
  @Query(
      "SELECT d FROM Deployment d "
          + "JOIN FETCH d.repository r "
          + "WHERE d.state IN :states "
          + "AND COALESCE(d.updatedAt, d.createdAt) < :threshold "
          + "ORDER BY COALESCE(d.updatedAt, d.createdAt) ASC")
  List<Deployment> findStaleIncompleteDeployments(
      @Param("threshold") OffsetDateTime threshold,
      @Param("states") List<Deployment.State> states,
      Pageable pageable);
}
