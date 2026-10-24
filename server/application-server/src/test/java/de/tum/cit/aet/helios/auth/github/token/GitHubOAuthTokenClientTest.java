package de.tum.cit.aet.helios.auth.github.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.github.GitHubConfig;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GitHubOAuthTokenClientTest {

  private final OkHttpClient okHttpClient = mock(OkHttpClient.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GitHubConfig gitHubConfig = mock(GitHubConfig.class);
  private GitHubOAuthTokenClient client;

  @BeforeEach
  void setUp() {
    when(gitHubConfig.getClientId()).thenReturn("cid");
    when(gitHubConfig.getClientSecret()).thenReturn("secret");
    client = new GitHubOAuthTokenClient(okHttpClient, objectMapper, gitHubConfig);
  }

  private static final String URL = "https://github.com/login/oauth/access_token";

  private void stub(int code, String body) throws IOException {
    Response response =
        new Response.Builder()
            .request(new Request.Builder().url(URL).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("m")
            .body(ResponseBody.create(body, MediaType.parse("application/json")))
            .build();
    Call call = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
    when(call.execute()).thenReturn(response);
  }

  @Test
  void parsesRefreshedTokensAndExpiries() throws IOException {
    stub(
        200,
        "{\"access_token\":\"ghu_new\",\"expires_in\":28800,\"refresh_token\":\"ghr_new\","
            + "\"refresh_token_expires_in\":15897600,\"token_type\":\"bearer\"}");

    GitHubUserTokenRecord record = client.refresh("ghr_old");

    assertEquals("ghu_new", record.accessToken());
    assertEquals("ghr_new", record.refreshToken());
    assertNotNull(record.accessTokenExpiresAt());
    assertNotNull(record.refreshTokenExpiresAt());
  }

  @Test
  void sendsRefreshGrantWithClientCredentials() throws IOException {
    stub(200, "{\"access_token\":\"ghu_new\",\"expires_in\":28800}");

    client.refresh("ghr_old");

    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(okHttpClient).newCall(captor.capture());
    Request request = captor.getValue();
    assertEquals("application/json", request.header("Accept"));

    FormBody form = (FormBody) request.body();
    Map<String, String> fields = new HashMap<>();
    for (int i = 0; i < form.size(); i++) {
      fields.put(form.name(i), form.value(i));
    }
    assertEquals("refresh_token", fields.get("grant_type"));
    assertEquals("ghr_old", fields.get("refresh_token"));
    assertEquals("cid", fields.get("client_id"));
    assertEquals("secret", fields.get("client_secret"));
  }

  @Test
  void missingRefreshTokenInResponseYieldsNull() throws IOException {
    stub(200, "{\"access_token\":\"ghu_new\",\"expires_in\":28800}");

    GitHubUserTokenRecord record = client.refresh("ghr_old");

    assertEquals("ghu_new", record.accessToken());
    assertNull(record.refreshToken());
    assertNull(record.refreshTokenExpiresAt());
  }

  @Test
  void oauthErrorBodyThrowsReauthRequired() throws IOException {
    // GitHub returns HTTP 200 with an error body for a bad refresh token.
    stub(200, "{\"error\":\"bad_refresh_token\",\"error_description\":\"expired\"}");

    assertThrows(GitHubReauthRequiredException.class, () -> client.refresh("ghr_old"));
  }

  @Test
  void serverErrorIsTransientIoExceptionNotReauth() throws IOException {
    stub(500, "upstream boom");

    IOException e = assertThrows(IOException.class, () -> client.refresh("ghr_old"));
    assertFalse(e instanceof GitHubReauthRequiredException);
  }
}
