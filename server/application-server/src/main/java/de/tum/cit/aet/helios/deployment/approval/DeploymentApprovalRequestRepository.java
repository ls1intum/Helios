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
   * Outstanding requests for a reviewer in the given state, used by the in-app pending-approvals
   * list. Join-fetches everything the row's DTO touches (deployment, environment, repo, creator)
   * so the list renders without N+1 queries and remains correct even if any of those associations
   * is later switched to LAZY or {@code spring.jpa.open-in-view=false}.
   *
   * <p>The state is passed as a parameter rather than inlined as a JPQL enum literal — the
   * inlined form needs the non-portable nested-class {@code $} separator and is brittle to
   * Hibernate parser changes.
   */
  @Query(
      "select r from DeploymentApprovalRequest r "
          + "join fetch r.heliosDeployment d "
          + "left join fetch d.creator "
          + "join fetch d.environment e "
          + "join fetch e.repository "
          + "where r.reviewer.id = :reviewerId and r.state = :state "
          + "order by r.createdAt desc")
  List<DeploymentApprovalRequest> findByReviewerAndState(
      @Param("reviewerId") Long reviewerId,
      @Param("state") DeploymentApprovalRequest.State state);

  long countByReviewerIdAndState(Long reviewerId, DeploymentApprovalRequest.State state);
}
