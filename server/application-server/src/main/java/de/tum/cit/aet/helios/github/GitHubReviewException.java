package de.tum.cit.aet.helios.github;

import java.io.IOException;

/**
 * Raised when GitHub rejects a pending-deployment review (approve/reject) call. Carries the
 * upstream HTTP status so callers can react to it — most importantly {@code 401}, which means the
 * impersonated user's GitHub token (brokered through Keycloak) has expired and the reviewer needs
 * to re-authenticate.
 *
 * <p>Extends {@link IOException} so existing {@code throws IOException} signatures and
 * {@code catch (IOException ...)} handlers on the review path keep working unchanged.
 */
public class GitHubReviewException extends IOException {

  private final int httpStatus;

  public GitHubReviewException(int httpStatus, String message) {
    super(message);
    this.httpStatus = httpStatus;
  }

  /** The HTTP status GitHub returned (e.g. 401 for an expired/invalid token). */
  public int getHttpStatus() {
    return httpStatus;
  }
}
