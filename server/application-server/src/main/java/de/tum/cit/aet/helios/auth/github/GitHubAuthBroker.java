package de.tum.cit.aet.helios.auth.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class GitHubAuthBroker {

  private final ObjectMapper objectMapper;
  private final OkHttpClient okHttpClient;

  @Value("${github.tokenExchangeClientId}")
  private String tokenExchangeClient;

  @Value("${github.tokenExchangeClientSecret}")
  private String tokenExchangeSecret;

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  private String issuerUri;

  /**
   * Performs a token exchange operation with the identity provider.
   *
   * @param username the username for which to request a token
   * @return the token exchange response
   * @throws IOException if an I/O error occurs during the token exchange
   */
  public TokenExchangeResponse exchangeToken(String username) throws IOException {

    FormBody requestBody =
        new FormBody.Builder()
            .add("client_id", tokenExchangeClient)
            .add("client_secret", tokenExchangeSecret)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
            .add("requested_subject", username)
            .add("requested_token_type", "urn:ietf:params:oauth:token-type:access_token")
            .add("requested_issuer", "github")
            .build();

    Request request =
        new Request.Builder()
            .url(issuerUri + "/protocol/openid-connect/token")
            .post(requestBody)
            .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Token exchange failed with response code: " + response.code());
      }

      ResponseBody responseBody = response.body();
      if (responseBody == null) {
        throw new IOException("Response body is null");
      }

      String responseBodyContent = responseBody.string();
      if (responseBodyContent.isEmpty()) {
        throw new IOException("Response body is empty");
      }

      return objectMapper.readValue(responseBodyContent, TokenExchangeResponse.class);
    } catch (JsonProcessingException e) {
      log.error("Error processing JSON response: {}", e.getMessage());
      throw new IOException("Error processing token exchange response", e);
    } catch (IOException e) {
      log.error("Error occurred during token exchange: {}", e.getMessage());
      throw e;
    }
  }

  /**
   * Retrieves the current user's GitHub token from Keycloak's broker token endpoint.
   *
   * <p>Keycloak refreshes stored external IDP tokens for this endpoint when the identity provider
   * returned a refresh token and the stored access token is close to expiry.
   *
   * @param keycloakAccessToken the current user's Keycloak access token
   * @return the GitHub token response
   * @throws IOException if the broker token retrieval fails
   */
  public TokenExchangeResponse retrieveCurrentUserGitHubToken(String keycloakAccessToken)
      throws IOException {
    Request request =
        new Request.Builder()
            .url(issuerUri + "/broker/github/token")
            .get()
            .header("Authorization", "Bearer " + keycloakAccessToken)
            .header("Accept", "application/json")
            .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      ResponseBody responseBody = response.body();
      String responseBodyContent = responseBody == null ? "" : responseBody.string();

      if (!response.isSuccessful()) {
        throw new IOException(
            "GitHub broker token retrieval failed with response code: "
                + response.code()
                + " and body: "
                + responseBodyContent);
      }

      if (responseBodyContent.isEmpty()) {
        throw new IOException("Response body is empty");
      }

      return objectMapper.readValue(responseBodyContent, TokenExchangeResponse.class);
    } catch (JsonProcessingException e) {
      log.error("Error processing broker token JSON response: {}", e.getMessage());
      throw new IOException("Error processing broker token response", e);
    } catch (IOException e) {
      log.error("Error occurred while retrieving broker token: {}", e.getMessage());
      throw e;
    }
  }
}
