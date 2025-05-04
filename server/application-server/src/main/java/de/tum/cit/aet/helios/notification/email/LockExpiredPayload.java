package de.tum.cit.aet.helios.notification.email;

/**
 * Payload for the {@code lock-expired} e‑mail notification.
 * This payload is used to inform users about an expired lock.
 *
 * @param username The username of the user who triggered the lock.
 * @param environment The environment where the lock expired.
 * @param repositoryId The ID of the repository where the lock expired.
 * @param repositoryName The name of the repository where the lock expired.
 */
public record LockExpiredPayload(
    String username,
    String environment,
    String repositoryId,
    String repositoryName
) implements EmailNotificationPayload {

  @Override
  public String template() {
    return "lock-expired";
  }

  @Override
  public String subject() {
    return "🔒 Lock expired – %s".formatted(environment);
  }
}