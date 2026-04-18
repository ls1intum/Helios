package de.tum.cit.aet.helios.heliosdeployment;

import de.tum.cit.aet.helios.environment.Environment;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HeliosDeploymentRepository extends JpaRepository<HeliosDeployment, Long> {

  Optional<HeliosDeployment> findTopByEnvironmentOrderByCreatedAtDesc(Environment environment);

  Optional<HeliosDeployment> findTopByBranchNameAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
      String branchName, OffsetDateTime eventTime);

  Optional<HeliosDeployment> findTopByEnvironmentAndBranchNameOrderByCreatedAtDesc(
      Environment environment, String branchName);

  List<HeliosDeployment> findByEnvironmentAndDeploymentIdIsNull(Environment environment);

  @SuppressWarnings("checkstyle:MethodName")
  List<HeliosDeployment> findByPullRequest_IdAndDeploymentIdIsNullOrderByCreatedAtDesc(
      Long pullRequestId);

  @Query(
      "SELECT hd FROM HeliosDeployment hd "
          + "JOIN hd.environment e "
          + "JOIN e.repository r "
          + "WHERE r.id = :repositoryId "
          + "AND hd.sha = :sha "
          + "ORDER BY hd.createdAt")
  List<HeliosDeployment> findByRepositoryIdAndSha(Long repositoryId, String sha);

  @Query(
      "SELECT hd FROM HeliosDeployment hd "
          + "JOIN hd.environment e "
          + "JOIN e.repository r "
          + "WHERE r.id = :repositoryId "
          + "AND hd.branchName = :branchName "
          + "AND hd.deploymentId IS NULL "
          + "ORDER BY hd.createdAt DESC")
  List<HeliosDeployment> findByRepositoryIdAndBranchNameAndDeploymentIdIsNullOrderByCreatedAtDesc(
      @Param("repositoryId") Long repositoryId, @Param("branchName") String branchName);

  Optional<HeliosDeployment> findByDeploymentId(Long deploymentId);

  Optional<HeliosDeployment> findByWorkflowRunId(Long workflowRunId);

  @Query(
      value =
          "SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY sub.build_duration_seconds)"
              + " AS median_build, "
              + "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY sub.deploy_duration_seconds)"
              + " AS median_deploy "
              + "FROM ("
              + "  SELECT build_duration_seconds, deploy_duration_seconds "
              + "  FROM helios_deployment "
              + "  WHERE environment_id = :environmentId "
              + "    AND status = 'DEPLOYMENT_SUCCESS' "
              + "    AND build_duration_seconds IS NOT NULL "
              + "  ORDER BY created_at DESC "
              + "  LIMIT 100"
              + ") sub",
      nativeQuery = true)
  List<Object[]> findMedianDurationsByEnvironmentId(@Param("environmentId") Long environmentId);

  /**
   * Finds deployments that are stuck in incomplete state for more than the specified duration.
   *
   * @param threshold Time threshold; deployments with statusUpdatedAt before this time
   * @return List of deployments stuck in incomplete state beyond the time threshold
   */
  @Query(
      "SELECT hd FROM HeliosDeployment hd "
          + "WHERE hd.status IN ('IN_PROGRESS', 'WAITING', 'QUEUED') "
          + "AND hd.statusUpdatedAt < :threshold "
          + "AND hd.deploymentId IS NULL")
  List<HeliosDeployment> findStuckDeploymentsWithoutDeploymentId(
      @Param("threshold") OffsetDateTime threshold);
}
