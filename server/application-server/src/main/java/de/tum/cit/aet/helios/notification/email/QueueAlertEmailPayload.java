package de.tum.cit.aet.helios.notification.email;

import de.tum.cit.aet.helios.notification.NotificationPreference;

/**
 * Payload for queue-related SLO alerts. Mirrors {@link LockReleasedPayload}; see plan §F.
 *
 * @param kind the rule kind that fired (QUEUE_P95_OVER / RUNNER_OFFLINE_OVER / STUCK_JOBS_OVER)
 * @param measuredValue the measured value at fire time
 * @param thresholdValue the rule threshold that was breached
 * @param repositoryName name of the affected repository (may be null for org-wide rules)
 * @param details free-form context (label set, runner names, etc.)
 */
public record QueueAlertEmailPayload(
    String kind,
    Integer measuredValue,
    Integer thresholdValue,
    String repositoryName,
    String details
) implements EmailNotificationPayload {

  @Override
  public String template() {
    return "queue-alert";
  }

  @Override
  public String subject() {
    String repoSuffix = repositoryName == null ? "" : " – " + repositoryName;
    return "🚨 Queue alert: %s%s".formatted(kind, repoSuffix);
  }

  @Override
  public NotificationPreference.Type type() {
    return switch (kind) {
      case "QUEUE_P95_OVER" -> NotificationPreference.Type.QUEUE_P95_BREACH;
      case "RUNNER_OFFLINE_OVER" -> NotificationPreference.Type.RUNNER_OFFLINE;
      case "STUCK_JOBS_OVER" -> NotificationPreference.Type.STUCK_JOBS;
      default -> NotificationPreference.Type.QUEUE_P95_BREACH;
    };
  }
}
