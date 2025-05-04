package de.tum.cit.aet.helios.util;

import de.tum.cit.aet.helios.user.User;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHWorkflowRun;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class DeploymentFailureNotificationDecider {

  private final StartupTimeHolder startupTimeHolder;

  /**
   * Max age of a workflow‑run to still trigger a mail.
   */
  private static final Duration MAX_AGE = Duration.ofMinutes(1);

  /**
   * Method to determine if a failure notification should be sent.
   *
   * @return {@code true} only when
   *     <ul>
   *       <li>run.conclusion == FAILURE</li>
   *       <li>updatedAt &gt; appReady</li>
   *       <li>updatedAt is &le; {@link #MAX_AGE} ago</li>
   *     </ul>
   */
  public boolean shouldNotify(GHWorkflowRun run, User user) {
    try {
      /* must be FAILURE */
      if (!GHWorkflowRun.Conclusion.FAILURE.equals(run.getConclusion())) {
        return false;
      }

      /* updatedAt must be obtainable */
      Instant updated;
      try {
        updated = run.getUpdatedAt().toInstant();
      } catch (IOException e) {
        log.warn("Cannot read updatedAt for run {}", run.getId(), e);
        return false;
      }

      /* ignore anything before the app was ready */
      if (updated.isBefore(startupTimeHolder.getReadyAt())) {
        return false;
      }

      /* ignore stale events */
      if (Duration.between(updated, Instant.now()).compareTo(MAX_AGE) > 0) {
        return false;
      }

      return true;
    } catch (Exception e) {
      log.error("Error while checking if deployment failed notification should be sent", e);
      return false;
    }

  }
}
