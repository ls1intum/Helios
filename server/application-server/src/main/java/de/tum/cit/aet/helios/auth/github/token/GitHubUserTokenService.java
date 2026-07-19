package de.tum.cit.aet.helios.auth.github.token;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
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
 *   <li>otherwise seed the refresh token from Keycloak (retrieve-token), then refresh.
 * </ol>
 *
 * <p>GitHub rotates refresh tokens on use, so every refresh persists the <em>new</em> refresh
 * token. When no refresh path can succeed, {@link GitHubReauthRequiredException} propagates so the
 * caller can tell the user to sign in again.
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
  private final GitHubOAuthTokenClient oauthClient;

  @Transactional
  public String getValidAccessToken(String githubLogin) throws IOException {
    OffsetDateTime now = OffsetDateTime.now();
    Optional<GitHubUserToken> existing = repository.findByGithubLogin(githubLogin);

    if (existing.isPresent()) {
      GitHubUserToken row = existing.get();

      if (row.getAccessTokenEnc() != null
          && row.getAccessTokenExpiresAt() != null
          && row.getAccessTokenExpiresAt().isAfter(now.plus(EXPIRY_MARGIN))) {
        return tokenCipher.decrypt(row.getAccessTokenEnc());
      }

      if (row.getRefreshTokenEnc() != null
          && (row.getRefreshTokenExpiresAt() == null
              || row.getRefreshTokenExpiresAt().isAfter(now))) {
        GitHubUserTokenRecord refreshed =
            oauthClient.refresh(tokenCipher.decrypt(row.getRefreshTokenEnc()));
        persist(row, refreshed);
        return refreshed.accessToken();
      }

      return seedThenRefresh(row, githubLogin);
    }

    GitHubUserToken row = new GitHubUserToken();
    row.setGithubLogin(githubLogin);
    return seedThenRefresh(row, githubLogin);
  }

  /**
   * Seeds the refresh token from Keycloak and immediately exchanges it for a known-fresh access
   * token (the seeded access token's real age is unknown, so we never trust it directly).
   */
  private String seedThenRefresh(GitHubUserToken row, String githubLogin) throws IOException {
    GitHubUserTokenRecord seeded = brokerClient.fetchStoredTokens(githubLogin);
    GitHubUserTokenRecord refreshed = oauthClient.refresh(seeded.refreshToken());
    persist(row, refreshed);
    return refreshed.accessToken();
  }

  private void persist(GitHubUserToken row, GitHubUserTokenRecord record) {
    row.setAccessTokenEnc(tokenCipher.encrypt(record.accessToken()));
    row.setAccessTokenExpiresAt(record.accessTokenExpiresAt());
    if (record.refreshToken() != null) {
      row.setRefreshTokenEnc(tokenCipher.encrypt(record.refreshToken()));
      row.setRefreshTokenExpiresAt(record.refreshTokenExpiresAt());
    }
    repository.save(row);
  }
}
