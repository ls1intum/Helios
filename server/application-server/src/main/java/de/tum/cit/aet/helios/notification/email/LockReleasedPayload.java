package de.tum.cit.aet.helios.notification.email;

/**
 * Payload for the {@code lock-released} e‑mail notification.
 * This payload is used to inform users about a released lock.
 *
 * @param username The username of the user who triggered the lock release.
 * @param lockReleaseUser The username of the user who released the lock.
 * @param environment The environment where the lock was released.
 * @param repositoryId The ID of the repository where the lock was released.
 * @param repositoryName The name of the repository where the lock was released.
 */
public record LockReleasedPayload(
    String username,
    String lockReleaseUser,
    String environment,
    String repositoryId,
    String repositoryName
) implements EmailNotificationPayload {

  @Override
  public String template() {
    return "lock-released";
  }

  @Override
  public String subject() {
    return "🔓 Lock released by %s – %s".formatted(lockReleaseUser, environment);
  }
}