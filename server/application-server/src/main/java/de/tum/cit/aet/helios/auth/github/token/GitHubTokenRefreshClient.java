package de.tum.cit.aet.helios.auth.github.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.github.GitHubConfig;
import java.io.IOException;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

/**
 * Refreshes a user's GitHub token directly against GitHub's OAuth token endpoint using the login
 * App's {@code client_id}/{@code client_secret} and a stored refresh token. GitHub rotates refresh
 * tokens on use, so the returned {@link GitHubUserTokenRecord} carries a <em>new</em> refresh token
 * the caller must persist.
 *
 * <p>GitHub returns HTTP 200 even for OAuth errors (e.g. {@code bad_refresh_token}), with an
 * {@code error} field in the body. Only {@code bad_refresh_token} means the user's token is dead
 * and surfaces as {@link GitHubReauthRequiredException}; every other failure (configuration, 5xx,
 * transport, malformed body) is a plain {@link IOException}.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class GitHubTokenRefreshClient {

  /** The only OAuth error that means "this user's refresh token is dead". */
  static final String REFRESH_TOKEN_REJECTED = "bad_refresh_token";

  private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";

  private final OkHttpClient okHttpClient;
  private final ObjectMapper objectMapper;
  private final GitHubConfig gitHubConfig;

  /**
   * Exchanges {@code refreshToken} for a fresh access token (and a rotated refresh token).
   *
   * @throws GitHubReauthRequiredException if GitHub rejects the refresh token (re-login needed)
   * @throws IOException on transport failure or a non-2xx response (transient)
   */
  public GitHubUserTokenRecord refresh(String refreshToken) throws IOException {
    String clientSecret = gitHubConfig.getClientSecret();
    if (clientSecret == null || clientSecret.isBlank()) {
      throw new IOException(
          "GitHub OAuth client secret is not configured (GITHUB_CLIENT_SECRET); cannot refresh "
              + "user tokens.");
    }

    FormBody body =
        new FormBody.Builder()
            .add("client_id", gitHubConfig.getClientId())
            .add("client_secret", clientSecret)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build();

    Request request =
        new Request.Builder()
            .url(TOKEN_URL)
            .post(body)
            .header("Accept", "application/json")
            .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      ResponseBody responseBody = response.body();
      String content = responseBody == null ? "" : responseBody.string();
      if (!response.isSuccessful()) {
        throw new IOException("GitHub token refresh failed: HTTP " + response.code());
      }

      JsonNode json = objectMapper.readTree(content);
      if (json.hasNonNull("error")) {
        String error = json.get("error").asText();
        if (REFRESH_TOKEN_REJECTED.equals(error)) {
          throw new GitHubReauthRequiredException(
              "GitHub rejected the stored refresh token: " + error);
        }
        // App-side problems (wrong client secret, unsupported grant, ...) cannot be fixed by the
        // user signing in again, so they must not be reported as re-authorization.
        throw new IOException("GitHub token refresh failed: " + error);
      }
      if (!json.hasNonNull("access_token") || json.get("access_token").asText().isBlank()) {
        throw new IOException("GitHub refresh response contained no access token.");
      }

      OffsetDateTime now = OffsetDateTime.now();
      String accessToken = json.get("access_token").asText();
      OffsetDateTime accessExpiry = now.plusSeconds(json.path("expires_in").asLong(0));
      String newRefreshToken =
          json.hasNonNull("refresh_token") ? json.get("refresh_token").asText() : null;
      OffsetDateTime refreshExpiry =
          json.hasNonNull("refresh_token_expires_in")
              ? now.plusSeconds(json.get("refresh_token_expires_in").asLong())
              : null;
      return new GitHubUserTokenRecord(accessToken, accessExpiry, newRefreshToken, refreshExpiry);
    }
  }
}
