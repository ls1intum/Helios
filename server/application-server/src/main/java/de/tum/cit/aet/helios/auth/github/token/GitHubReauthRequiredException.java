package de.tum.cit.aet.helios.auth.github.token;

import java.io.IOException;

/**
 * Signals that a user's GitHub authorization can no longer be refreshed — the refresh token was
 * revoked, expired, or rotated away — and the user must sign in again to re-establish it.
 *
 * <p>Extends {@link IOException} so it flows through the existing approval-path signatures; the
 * approval service maps it to an actionable "sign out and back in" message.
 */
public class GitHubReauthRequiredException extends IOException {

  public GitHubReauthRequiredException(String message) {
    super(message);
  }
}
