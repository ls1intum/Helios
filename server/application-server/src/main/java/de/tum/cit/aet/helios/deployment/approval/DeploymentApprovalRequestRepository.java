package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentApprovalRequestRepository
    extends JpaRepository<DeploymentApprovalRequest, Long> {

  List<DeploymentApprovalRequest> findByHeliosDeploymentAndState(
      HeliosDeployment heliosDeployment, DeploymentApprovalRequest.State state);

  List<DeploymentApprovalRequest> findByHeliosDeployment(HeliosDeployment heliosDeployment);

  Optional<DeploymentApprovalRequest> findByTokenHash(String tokenHash);

  /**
   * Take a pessimistic write lock on the deployment-approval row for the given reviewer. Used to
   * serialise approve/decline transactions for a single (deployment, reviewer) pair so the GitHub
   * call is not duplicated by concurrent clicks.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from DeploymentApprovalRequest r where r.heliosDeployment.id = :deploymentId "
          + "and r.reviewerLogin = :reviewerLogin")
  Optional<DeploymentApprovalRequest> lockByDeploymentAndReviewerLogin(
      @Param("deploymentId") Long deploymentId, @Param("reviewerLogin") String reviewerLogin);

  /**
   * Outstanding (PENDING) requests for a reviewer, used by the in-app pending-approvals list. Join
   * fetch the deployment + environment + repo so the list can render without N+1 queries.
   */
  @Query(
      "select r from DeploymentApprovalRequest r "
          + "join fetch r.heliosDeployment d "
          + "join fetch d.environment e "
          + "join fetch e.repository "
          + "where r.reviewer.id = :reviewerId and r.state = "
          + "de.tum.cit.aet.helios.deployment.approval.DeploymentApprovalRequest$State.PENDING "
          + "order by r.createdAt desc")
  List<DeploymentApprovalRequest> findPendingForReviewer(@Param("reviewerId") Long reviewerId);

  long countByReviewerIdAndState(Long reviewerId, DeploymentApprovalRequest.State state);
}
