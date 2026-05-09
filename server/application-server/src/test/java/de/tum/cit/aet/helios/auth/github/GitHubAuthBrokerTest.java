package de.tum.cit.aet.helios.auth.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GitHubAuthBrokerTest {

  private final ObjectMapper objectMapper = mock(ObjectMapper.class);
  private final OkHttpClient okHttpClient = mock(OkHttpClient.class);
  private final Call call = mock(Call.class);
  private final GitHubAuthBroker broker = new GitHubAuthBroker(objectMapper, okHttpClient);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        broker, "issuerUri", "http://localhost:8081/realms/helios-test");
  }

  @Test
  void retrieveCurrentUserGitHubTokenCallsBrokerEndpoint() throws IOException {
    String responseJson = "{\"access_token\":\"github-token\"}";
    TokenExchangeResponse tokenResponse = new TokenExchangeResponse();
    tokenResponse.setAccessToken("github-token");

    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
    when(call.execute())
        .thenReturn(
            new Response.Builder()
                .request(new Request.Builder().url("http://dummy").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(responseJson, MediaType.parse("application/json")))
                .build());
    when(objectMapper.readValue(responseJson, TokenExchangeResponse.class))
        .thenReturn(tokenResponse);

    TokenExchangeResponse result = broker.retrieveCurrentUserGitHubToken("keycloak-token");

    assertEquals("github-token", result.getAccessToken());
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(okHttpClient).newCall(requestCaptor.capture());
    Request request = requestCaptor.getValue();
    assertEquals("GET", request.method());
    assertEquals(
        "http://localhost:8081/realms/helios-test/broker/github/token",
        request.url().toString());
    assertEquals("Bearer keycloak-token", request.header("Authorization"));
    assertEquals("application/json", request.header("Accept"));
    verify(objectMapper).readValue(eq(responseJson), eq(TokenExchangeResponse.class));
  }

  @Test
  void retrieveCurrentUserGitHubTokenIncludesFailureBody() throws IOException {
    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
    when(call.execute())
        .thenReturn(
            new Response.Builder()
                .request(new Request.Builder().url("http://dummy").build())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body(
                    ResponseBody.create(
                        "{\"error\":\"forbidden\"}", MediaType.parse("application/json")))
                .build());

    IOException exception =
        assertThrows(
            IOException.class, () -> broker.retrieveCurrentUserGitHubToken("keycloak-token"));

    assertEquals(
        "GitHub broker token retrieval failed with response code: 403 and body:"
            + " {\"error\":\"forbidden\"}",
        exception.getMessage());
  }
}
