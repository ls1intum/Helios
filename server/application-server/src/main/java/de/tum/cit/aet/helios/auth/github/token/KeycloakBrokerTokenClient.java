package de.tum.cit.aet.helios.auth.github.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Seeds a user's GitHub refresh token from Keycloak's identity-broker retrieve-token endpoint —
 * the one place that returns the stored GitHub token <em>including</em> the refresh token (plain
 * token-exchange returns only the access token, and Keycloak never refreshes GitHub tokens itself).
 *
 * <p>Two steps: (1) mint an internal Keycloak access token for the user via impersonation
 * token-exchange (no {@code requested_issuer}); (2) call {@code GET /broker/github/token} with it.
 * Step 2 requires the token-exchange client to hold the retrieve-token permission on the {@code
 * github} IdP (otherwise Keycloak returns 403).
 *
 * <p>Keycloak may hand the stored GitHub token back as JSON or as the raw form-encoded body GitHub
 * originally returned, so both are parsed.
 */
@Log4j2
@Component
public class KeycloakBrokerTokenClient {

  private final OkHttpClient okHttpClient;
  private final ObjectMapper objectMapper;
  private final String issuerUri;
  private final String tokenExchangeClient;
  private final String tokenExchangeSecret;

  public KeycloakBrokerTokenClient(
      OkHttpClient okHttpClient,
      ObjectMapper objectMapper,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${github.tokenExchangeClientId}") String tokenExchangeClient,
      @Value("${github.tokenExchangeClientSecret}") String tokenExchangeSecret) {
    this.okHttpClient = okHttpClient;
    this.objectMapper = objectMapper;
    this.issuerUri = issuerUri;
    this.tokenExchangeClient = tokenExchangeClient;
    this.tokenExchangeSecret = tokenExchangeSecret;
  }

  /**
   * Retrieves the stored GitHub tokens for {@code githubLogin}.
   *
   * @throws GitHubReauthRequiredException if Keycloak holds no refresh token for the user (they
   *     must sign in through GitHub again)
   * @throws IOException on transport failure or a non-2xx Keycloak response
   */
  public GitHubUserTokenRecord fetchStoredTokens(String githubLogin) throws IOException {
    String internalToken = exchangeForInternalToken(githubLogin);
    String storedBody = retrieveStoredGitHubToken(internalToken);
    Map<String, String> fields = parseTokenBody(storedBody);

    String refreshToken = fields.get("refresh_token");
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new GitHubReauthRequiredException(
          "Keycloak returned no GitHub refresh token for @" + githubLogin
              + "; the user must sign in to Helios again.");
    }
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime accessExpiry = now.plusSeconds(parseSeconds(fields.get("expires_in")));
    OffsetDateTime refreshExpiry =
        fields.containsKey("refresh_token_expires_in")
            ? now.plusSeconds(parseSeconds(fields.get("refresh_token_expires_in")))
            : null;
    return new GitHubUserTokenRecord(
        fields.get("access_token"), accessExpiry, refreshToken, refreshExpiry);
  }

  private String exchangeForInternalToken(String githubLogin) throws IOException {
    FormBody form =
        new FormBody.Builder()
            .add("client_id", tokenExchangeClient)
            .add("client_secret", tokenExchangeSecret)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
            .add("requested_subject", githubLogin)
            .add("requested_token_type", "urn:ietf:params:oauth:token-type:access_token")
            .build();
    Request request =
        new Request.Builder().url(issuerUri + "/protocol/openid-connect/token").post(form).build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      ResponseBody body = response.body();
      String content = body == null ? "" : body.string();
      if (!response.isSuccessful()) {
        throw new IOException("Keycloak token exchange failed: HTTP " + response.code());
      }
      JsonNode json = objectMapper.readTree(content);
      if (!json.hasNonNull("access_token") || json.get("access_token").asText().isBlank()) {
        throw new IOException("Keycloak token exchange returned no access token.");
      }
      return json.get("access_token").asText();
    }
  }

  private String retrieveStoredGitHubToken(String internalToken) throws IOException {
    Request request =
        new Request.Builder()
            .url(issuerUri + "/broker/github/token")
            .get()
            .header("Authorization", "Bearer " + internalToken)
            .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      ResponseBody body = response.body();
      String content = body == null ? "" : body.string();
      if (!response.isSuccessful()) {
        throw new IOException(
            "Keycloak retrieve-token failed: HTTP " + response.code()
                + " (does the token-exchange client have retrieve-token permission on the github "
                + "IdP?)");
      }
      return content;
    }
  }

  /** Parses the stored GitHub token, accepting either a JSON object or a form-encoded string. */
  private Map<String, String> parseTokenBody(String body) throws IOException {
    Map<String, String> fields = new HashMap<>();
    String trimmed = body == null ? "" : body.trim();
    if (trimmed.startsWith("{")) {
      JsonNode json = objectMapper.readTree(trimmed);
      json.fields()
          .forEachRemaining(
              entry -> {
                if (entry.getValue().isValueNode()) {
                  fields.put(entry.getKey(), entry.getValue().asText());
                }
              });
    } else {
      for (String pair : trimmed.split("&")) {
        int eq = pair.indexOf('=');
        if (eq > 0) {
          String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
          String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
          fields.put(key, value);
        }
      }
    }
    return fields;
  }

  private static long parseSeconds(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }
}
