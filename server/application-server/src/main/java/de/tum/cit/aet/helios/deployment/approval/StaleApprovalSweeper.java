package de.tum.cit.aet.helios.deployment.approval;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archives approval requests that can no longer be acted on. GitHub fails a deployment that has
 * waited for approval for 30 days, so any request still {@code PENDING} or {@code FAILED_AT_GITHUB}
 * after {@link DeploymentApprovalRequest#GITHUB_APPROVAL_WINDOW} is flipped to {@code EXPIRED} and
 * leaves the reviewer's pending list for good.
 *
 * <p>The list query already hides such rows immediately, so this sweep only keeps the table
 * honest for audit and reporting.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class StaleApprovalSweeper {

  static final String EXPIRY_REASON =
      "The deployment waited longer than GitHub's 30-day approval window and can no longer be "
          + "approved or declined.";

  private final DeploymentApprovalRequestRepository repository;
  private final org.springframework.core.env.Environment springEnvironment;

  /** Shortly after start-up, then every six hours. */
  @Scheduled(initialDelay = 120_000, fixedDelay = 6 * 60 * 60 * 1000)
  @Transactional
  public void sweep() {
    if (springEnvironment.matchesProfiles("openapi")) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now();
    int archived =
        repository.expireOpenRequestsCreatedBefore(
            now.minus(DeploymentApprovalRequest.GITHUB_APPROVAL_WINDOW),
            now,
            EXPIRY_REASON,
            DeploymentApprovalRequest.State.EXPIRED,
            List.of(
                DeploymentApprovalRequest.State.PENDING,
                DeploymentApprovalRequest.State.FAILED_AT_GITHUB));
    if (archived > 0) {
      log.info("Archived {} approval request(s) older than GitHub's approval window.", archived);
    }
  }
}
