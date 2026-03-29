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

  Optional<HeliosDeployment> findTopByWorkflowBranchAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
      String workflowBranch, OffsetDateTime eventTime);

  Optional<HeliosDeployment> findTopByEnvironmentAndBranchNameOrderByCreatedAtDesc(
      Environment environment, String branchName);

  Optional<HeliosDeployment> findTopByEnvironmentAndWorkflowBranchOrderByCreatedAtDesc(
      Environment environment, String workflowBranch);

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

  List<HeliosDeployment> findByDeploymentIdIn(java.util.Collection<Long> deploymentIds);

  Optional<HeliosDeployment> findByWorkflowRunId(Long workflowRunId);


  /**
   * Finds deployments that are stuck in IN_PROGRESS state for more than the specified duration.
   *
   * @param threshold Time threshold; deployments with statusUpdatedAt before this time
   * @return List of deployments stuck in IN_PROGRESS state beyond the time threshold
   */
  @Query(
      "SELECT hd FROM HeliosDeployment hd "
          + "WHERE hd.status = 'IN_PROGRESS' "
          + "AND (hd.statusUpdatedAt < :threshold)")
  List<HeliosDeployment> findStuckDeployments(@Param("threshold") OffsetDateTime threshold);
}
