package de.tum.cit.aet.helios.auth.github.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class GitHubUserTokenServiceTest {

  private static final String LOGIN = "octocat";
  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  private final GitHubUserTokenRepository repository = mock(GitHubUserTokenRepository.class);
  private final TokenCipher cipher = new TokenCipher(KEY);
  private final KeycloakBrokerTokenClient brokerClient = mock(KeycloakBrokerTokenClient.class);
  private final GitHubTokenRefreshClient oauthClient = mock(GitHubTokenRefreshClient.class);
  private final GitHubUserTokenService service =
      new GitHubUserTokenService(repository, cipher, brokerClient, oauthClient);

  private GitHubUserToken row() {
    GitHubUserToken row = new GitHubUserToken();
    row.setGithubLogin(LOGIN);
    return row;
  }

  @Test
  void freshCachedAccessTokenIsReturnedWithoutNetwork() throws IOException {
    GitHubUserToken row = row();
    row.setAccessTokenEnc(cipher.encrypt("ghu_cached"));
    row.setAccessTokenExpiresAt(OffsetDateTime.now().plusHours(1));
    when(repository.findByGithubLogin(LOGIN)).thenReturn(Optional.of(row));

    assertEquals("ghu_cached", service.getValidAccessToken(LOGIN));
    verifyNoInteractions(oauthClient, brokerClient);
    verify(repository, never()).save(any());
  }

  @Test
  void expiredAccessTokenIsRefreshedRotatedAndPersisted() throws IOException {
    GitHubUserToken row = row();
    row.setAccessTokenEnc(cipher.encrypt("ghu_old"));
    row.setAccessTokenExpiresAt(OffsetDateTime.now().minusHours(1));
    row.setRefreshTokenEnc(cipher.encrypt("ghr_old"));
    row.setRefreshTokenExpiresAt(OffsetDateTime.now().plusDays(30));
    when(repository.findByGithubLogin(LOGIN)).thenReturn(Optional.of(row));
    when(oauthClient.refresh("ghr_old"))
        .thenReturn(
            new GitHubUserTokenRecord(
                "ghu_new",
                OffsetDateTime.now().plusHours(8),
                "ghr_new",
                OffsetDateTime.now().plusMonths(6)));

    assertEquals("ghu_new", service.getValidAccessToken(LOGIN));
    verifyNoInteractions(brokerClient);

    ArgumentCaptor<GitHubUserToken> captor = ArgumentCaptor.forClass(GitHubUserToken.class);
    verify(repository).save(captor.capture());
    assertEquals("ghu_new", cipher.decrypt(captor.getValue().getAccessTokenEnc()));
    assertEquals("ghr_new", cipher.decrypt(captor.getValue().getRefreshTokenEnc()));
  }

  @Test
  void noRowSeedsFromKeycloakThenRefreshes() throws IOException {
    when(repository.findByGithubLogin(LOGIN)).thenReturn(Optional.empty());
    when(brokerClient.fetchStoredTokens(LOGIN))
        .thenReturn(
            new GitHubUserTokenRecord(
                "ghu_seed", OffsetDateTime.now(), "ghr_seed", OffsetDateTime.now().plusMonths(6)));
    when(oauthClient.refresh("ghr_seed"))
        .thenReturn(
            new GitHubUserTokenRecord(
                "ghu_new",
                OffsetDateTime.now().plusHours(8),
                "ghr_new",
                OffsetDateTime.now().plusMonths(6)));

    assertEquals("ghu_new", service.getValidAccessToken(LOGIN));

    ArgumentCaptor<GitHubUserToken> captor = ArgumentCaptor.forClass(GitHubUserToken.class);
    verify(repository).save(captor.capture());
    assertEquals(LOGIN, captor.getValue().getGithubLogin());
    assertEquals("ghr_new", cipher.decrypt(captor.getValue().getRefreshTokenEnc()));
  }

  @Test
  void expiredRefreshTokenTriggersReseed() throws IOException {
    GitHubUserToken row = row();
    row.setAccessTokenEnc(cipher.encrypt("ghu_old"));
    row.setAccessTokenExpiresAt(OffsetDateTime.now().minusHours(1));
    row.setRefreshTokenEnc(cipher.encrypt("ghr_expired"));
    row.setRefreshTokenExpiresAt(OffsetDateTime.now().minusDays(1));
    when(repository.findByGithubLogin(LOGIN)).thenReturn(Optional.of(row));
    when(brokerClient.fetchStoredTokens(LOGIN))
        .thenReturn(
            new GitHubUserTokenRecord(
                "ghu_seed", OffsetDateTime.now(), "ghr_seed", OffsetDateTime.now().plusMonths(6)));
    when(oauthClient.refresh("ghr_seed"))
        .thenReturn(
            new GitHubUserTokenRecord(
                "ghu_new", OffsetDateTime.now().plusHours(8), "ghr_new", null));

    assertEquals("ghu_new", service.getValidAccessToken(LOGIN));
    verify(oauthClient, never()).refresh("ghr_expired");
  }

  private GitHubUserToken staleRowWithRefreshToken(String refreshToken) {
    GitHubUserToken row = row();
    row.setAccessTokenEnc(cipher.encrypt("ghu_old"));
    row.setAccessTokenExpiresAt(OffsetDateTime.now().minusHours(1));
    row.setRefreshTokenEnc(cipher.encrypt(refreshToken));
    row.setRefreshTokenExpiresAt(OffsetDateTime.now().plusDays(30));
    return row;
  }

  @Test
  void rejectedStoredRefreshTokenIsReplacedByFreshKeycloakSeed() throws IOException {
    // The user signed in again after we stored the (now dead) refresh token.
    GitHubUserToken row = staleRowWithRefreshToken("ghr_dead");
    when(repository.findByGithubLogin(LOGIN)).thenReturn(Optional.of(row));
    when(oauthClient.refresh("ghr_dead"))
        .thenThrow(new GitHubReauthRequiredException("bad_refresh_token"));
    when(brokerClient.fetchStoredTokens(LOGIN))
        .thenReturn(
            new GitHubUserTokenRecord(
                "ghu_seed", OffsetDateTime.now(), "ghr_seed", OffsetDateTime.now().plusMonths(6)));
    when(oauthClient.refresh("ghr_seed"))
        .thenReturn(
            new GitHubUserTokenRecord(
                "ghu_new",
                OffsetDateTime.now().plusHours(8),
                "ghr_new",
                OffsetDateTime.now().plusMonths(6)));

    assertEquals("ghu_new", service.getValidAccessToken(LOGIN));

    ArgumentCaptor<GitHubUserToken> captor = ArgumentCaptor.forClass(GitHubUserToken.class);
    verify(repository).save(captor.capture());
    assertEquals("ghr_new", cipher.decrypt(captor.getValue().getRefreshTokenEnc()));
  }

  @Test
  void reauthRequiredWhenStoredAndSeededRefreshTokensAreBothRejected() throws IOException {
    GitHubUserToken row = staleRowWithRefreshToken("ghr_dead");
    when(repository.findByGithubLogin(LOGIN)).thenReturn(Optional.of(row));
    when(brokerClient.fetchStoredTokens(LOGIN))
        .thenReturn(
            new GitHubUserTokenRecord(
                "ghu_seed", OffsetDateTime.now(), "ghr_seed_dead", null));
    when(oauthClient.refresh(anyString()))
        .thenThrow(new GitHubReauthRequiredException("bad_refresh_token"));

    assertThrows(GitHubReauthRequiredException.class, () -> service.getValidAccessToken(LOGIN));
    verify(repository, never()).save(any());
  }

  @Test
  void transientRefreshFailureDoesNotTriggerReseed() throws IOException {
    GitHubUserToken row = staleRowWithRefreshToken("ghr_old");
    when(repository.findByGithubLogin(LOGIN)).thenReturn(Optional.of(row));
    when(oauthClient.refresh("ghr_old")).thenThrow(new IOException("HTTP 502"));

    assertThrows(IOException.class, () -> service.getValidAccessToken(LOGIN));
    verifyNoInteractions(brokerClient);
  }

  @Test
  void loginIsNormalizedAndLockedBeforeAnyRead() throws IOException {
    GitHubUserToken row = row();
    row.setAccessTokenEnc(cipher.encrypt("ghu_cached"));
    row.setAccessTokenExpiresAt(OffsetDateTime.now().plusHours(1));
    when(repository.findByGithubLogin(LOGIN)).thenReturn(Optional.of(row));

    assertEquals("ghu_cached", service.getValidAccessToken("OctoCat"));

    InOrder order = inOrder(repository);
    order.verify(repository).lockLogin(LOGIN);
    order.verify(repository).findByGithubLogin(LOGIN);
  }
}
