package de.tum.cit.aet.helios.deployment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
  List<Deployment> findByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);

  List<Deployment> findByRepositoryRepositoryIdAndSha(Long repositoryId, String sha);

  Optional<Deployment> findFirstByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);

  /**
   * Finds deployments that are stuck in IN_PROGRESS state for more than the specified duration.
   *
   * @param threshold Time threshold; deployments with statusUpdatedAt before this time
   * @return List of deployments stuck in IN_PROGRESS state beyond the time threshold
   */
  @Query(
      "SELECT d FROM Deployment d "
          + "WHERE d.state = 'IN_PROGRESS' "
          + "AND (COALESCE(d.updatedAt, d.createdAt) < :threshold)")
  List<Deployment> findStuckDeployments(@Param("threshold") OffsetDateTime threshold);
}
