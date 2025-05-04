package de.tum.cit.aet.helios.util;

import de.tum.cit.aet.helios.user.User;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHWorkflowRun;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class DeploymentFailureNotificationDecider {

  private final StartupTimeHolder startupTimeHolder;
  private final Environment springEnv;

  /**
   * Max age of a workflow‑run to still trigger a mail.
   */
  private static final Duration MAX_AGE = Duration.ofMinutes(1);

  /**
   * Users who MAY receive “deployment‑failure” mails on the *staging* profile.
   */
  private static final Set<String> STAGING_ALLOWLIST = Set.of(
      "egekocabas",
      "turkerkoc",
      "gbanu"
  );

  /**
   * Method to determine if a failure notification should be sent.
   *
   * @return {@code true} only when
   *     <ul>
   *       <li>run.conclusion == FAILURE</li>
   *       <li>updatedAt &gt; appReady</li>
   *       <li>updatedAt is &le; {@link #MAX_AGE} ago</li>
   *       <li>on the <em>staging</em> profile: author is in {@link #STAGING_ALLOWLIST}</li>
   *     </ul>
   */
  public boolean shouldNotify(GHWorkflowRun run, User user) {
    try {
      /* 1 — must be FAILURE */
      if (!GHWorkflowRun.Conclusion.FAILURE.equals(run.getConclusion())) {
        return false;
      }

      /* 2 — updatedAt must be obtainable */
      Instant updated;
      try {
        updated = run.getUpdatedAt().toInstant();
      } catch (IOException e) {
        log.warn("Cannot read updatedAt for run {}", run.getId(), e);
        return false;
      }

      /* 3 — ignore anything before the app was ready */
      if (updated.isBefore(startupTimeHolder.getReadyAt())) {
        return false;
      }

      /* 4 — ignore stale events */
      if (Duration.between(updated, Instant.now()).compareTo(MAX_AGE) > 0) {
        return false;
      }

      /* 5 — staging profile: user must be whitelisted */
      if (springEnv.acceptsProfiles(Profiles.of("staging"))
          && !STAGING_ALLOWLIST.contains(user.getLogin().toLowerCase())) {
        log.debug("Staging mode: user '{}' not in allow‑list → no mail", user.getLogin());
        return false;
      }

      // all checks passed
      return true;
    } catch (Exception e) {
      log.error("Error while checking if deployment failed notification should be sent", e);
      return false;
    }

  }
}
