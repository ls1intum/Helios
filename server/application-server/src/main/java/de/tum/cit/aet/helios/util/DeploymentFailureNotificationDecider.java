package de.tum.cit.aet.helios.util;

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
   * Returns <code>true</code> if the run finished *after* we were online and is max 1 min old.
   */
  public boolean shouldNotify(GHWorkflowRun run) {
    Instant updated = null;
    try {
      updated = run.getUpdatedAt().toInstant();
    } catch (IOException e) {
      log.warn("Failed to get updatedAt from workflow run. Assuming it is not a failure.");
      return false;
    }
    Instant now = Instant.now();

    if (updated.isBefore(startupTimeHolder.getReadyAt())) {
      // came in during initial sync -> skip
      return false;
    }
    if (Duration.between(updated, now).toMinutes() > 1) {
      // stale event -> skip
      return false;
    }
    return GHWorkflowRun.Conclusion.FAILURE.equals(run.getConclusion());
  }
}
