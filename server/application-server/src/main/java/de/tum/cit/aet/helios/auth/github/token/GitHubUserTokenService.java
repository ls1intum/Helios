package de.tum.cit.aet.helios.auth.github.token;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out a currently-valid GitHub <em>user</em> access token for {@code githubLogin}, refreshing
 * as needed, so deployment approvals no longer depend on how recently the user logged in.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>a cached access token that is still comfortably valid → return it (no network);
 *   <li>otherwise a stored, unexpired refresh token → refresh against GitHub, persist the rotated
 *       tokens, return the new access token;
 *   <li>otherwise (no row, expired refresh token, or the stored one was rejected) seed the refresh
 *       token from Keycloak (retrieve-token), then refresh.
 * </ol>
 *
 * <p>GitHub rotates refresh tokens on use and each one is single-use, so the whole sequence runs
 * under a per-login advisory lock and every refresh persists the <em>new</em> refresh token. When
 * no refresh path can succeed, {@link GitHubReauthRequiredException} propagates so the caller can
 * tell the user to sign in again.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class GitHubUserTokenService {

  /** Refresh a little before actual expiry so a token handed out is still valid on use. */
  private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

  private final GitHubUserTokenRepository repository;
  private final TokenCipher tokenCipher;
  private final KeycloakBrokerTokenClient brokerClient;
  private final GitHubTokenRefreshClient refreshClient;

  /**
   * Returns a valid access token for the login. Concurrent calls for the same login (also across
   * application instances) are serialized, so a single-use refresh token is never consumed twice.
   */
  @Transactional
  public String getValidAccessToken(String githubLogin) throws IOException {
    String login = githubLogin.toLowerCase(Locale.ROOT);
    repository.lockLogin(login);

    GitHubUserToken row = repository.findByGithubLogin(login).orElseGet(() -> newRow(login));
    OffsetDateTime now = OffsetDateTime.now();

    if (row.getAccessTokenEnc() != null
        && row.getAccessTokenExpiresAt() != null
        && row.getAccessTokenExpiresAt().isAfter(now.plus(EXPIRY_MARGIN))) {
      return tokenCipher.decrypt(row.getAccessTokenEnc());
    }

    if (row.getRefreshTokenEnc() != null
        && (row.getRefreshTokenExpiresAt() == null
            || row.getRefreshTokenExpiresAt().isAfter(now))) {
      try {
        return refreshAndPersist(row, tokenCipher.decrypt(row.getRefreshTokenEnc()));
      } catch (GitHubReauthRequiredException e) {
        // Our stored token is dead, but the user may have signed in again since, which gives
        // Keycloak a newer one. Only if that fails too does the user need to act.
        log.info("Stored refresh token for {} was rejected; reseeding from Keycloak", login);
      }
    }

    // The seeded access token's real age is unknown, so we never use it directly: exchange the
    // seeded refresh token for a known-fresh pair.
    return refreshAndPersist(row, brokerClient.fetchStoredTokens(login).refreshToken());
  }

  private GitHubUserToken newRow(String login) {
    GitHubUserToken row = new GitHubUserToken();
    row.setGithubLogin(login);
    return row;
  }

  private String refreshAndPersist(GitHubUserToken row, String refreshToken) throws IOException {
    GitHubUserTokenRecord refreshed = refreshClient.refresh(refreshToken);
    row.setAccessTokenEnc(tokenCipher.encrypt(refreshed.accessToken()));
    row.setAccessTokenExpiresAt(refreshed.accessTokenExpiresAt());
    if (refreshed.refreshToken() != null) {
      row.setRefreshTokenEnc(tokenCipher.encrypt(refreshed.refreshToken()));
      row.setRefreshTokenExpiresAt(refreshed.refreshTokenExpiresAt());
    }
    repository.save(row);
    return refreshed.accessToken();
  }
}
