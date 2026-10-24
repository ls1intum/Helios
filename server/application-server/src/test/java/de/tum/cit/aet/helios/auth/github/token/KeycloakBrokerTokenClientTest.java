package de.tum.cit.aet.helios.auth.github.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeycloakBrokerTokenClientTest {

  private static final String ISSUER = "https://kc.example/realms/helios";

  private final OkHttpClient okHttpClient = mock(OkHttpClient.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private KeycloakBrokerTokenClient client;

  @BeforeEach
  void setUp() {
    client =
        new KeycloakBrokerTokenClient(okHttpClient, objectMapper, ISSUER, "tec", "tes");
  }

  private static Response response(int code, String body) {
    return new Response.Builder()
        .request(new Request.Builder().url("https://kc.example/x").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("m")
        .body(ResponseBody.create(body, MediaType.parse("application/json")))
        .build();
  }

  /** Stubs the two sequential HTTP calls: token-exchange, then retrieve-token. */
  private void stubExchangeThenRetrieve(Response exchange, Response retrieve) throws IOException {
    Call call = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
    when(call.execute()).thenReturn(exchange, retrieve);
  }

  @Test
  void parsesJsonRetrieveBody() throws IOException {
    stubExchangeThenRetrieve(
        response(200, "{\"access_token\":\"internal-kc\"}"),
        response(
            200,
            "{\"access_token\":\"ghu_seed\",\"expires_in\":28800,\"refresh_token\":\"ghr_seed\","
                + "\"refresh_token_expires_in\":15897600}"));

    GitHubUserTokenRecord record = client.fetchStoredTokens("octocat");

    assertEquals("ghu_seed", record.accessToken());
    assertEquals("ghr_seed", record.refreshToken());
    assertNotNull(record.refreshTokenExpiresAt());
  }

  @Test
  void parsesFormEncodedRetrieveBody() throws IOException {
    stubExchangeThenRetrieve(
        response(200, "{\"access_token\":\"internal-kc\"}"),
        response(
            200,
            "access_token=ghu_seed&expires_in=28800&refresh_token=ghr_seed"
                + "&refresh_token_expires_in=15897600&token_type=bearer"));

    GitHubUserTokenRecord record = client.fetchStoredTokens("octocat");

    assertEquals("ghu_seed", record.accessToken());
    assertEquals("ghr_seed", record.refreshToken());
    assertNotNull(record.refreshTokenExpiresAt());
  }

  @Test
  void noRefreshTokenInStoredBodyThrowsReauthRequired() throws IOException {
    stubExchangeThenRetrieve(
        response(200, "{\"access_token\":\"internal-kc\"}"),
        response(200, "{\"access_token\":\"ghu_seed\",\"expires_in\":28800}"));

    assertThrows(
        GitHubReauthRequiredException.class, () -> client.fetchStoredTokens("octocat"));
  }

  @Test
  void tokenExchangeFailureThrowsIoException() throws IOException {
    Call call = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
    when(call.execute()).thenReturn(response(401, "{\"error\":\"unauthorized_client\"}"));

    IOException e =
        assertThrows(IOException.class, () -> client.fetchStoredTokens("octocat"));
    // Not a reauth signal — this is a Keycloak/config failure, treated as transient.
    org.junit.jupiter.api.Assertions.assertFalse(e instanceof GitHubReauthRequiredException);
  }

  @Test
  void retrieveTokenForbiddenThrowsIoException() throws IOException {
    stubExchangeThenRetrieve(
        response(200, "{\"access_token\":\"internal-kc\"}"),
        response(
            403,
            "{\"errorMessage\":\"Client [helios-token-exchange] not authorized to retrieve "
                + "tokens from identity provider [github].\"}"));

    assertThrows(IOException.class, () -> client.fetchStoredTokens("octocat"));
  }
}
