package de.tum.cit.aet.helios.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthServiceTest {

  private final AuthService authService =
      new AuthService(
          mock(UserRepository.class), mock(GitHubFacade.class), mock(GitHubUserSyncService.class));

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getCurrentAccessTokenReturnsJwtTokenValue() {
    Jwt jwt =
        Jwt.withTokenValue("keycloak-access-token")
            .header("alg", "none")
            .claim("sub", "user-id")
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertEquals("keycloak-access-token", authService.getCurrentAccessToken());
  }

  @Test
  void getCurrentAccessTokenThrowsWithoutJwtAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "password"));

    assertThrows(IllegalStateException.class, authService::getCurrentAccessToken);
  }
}
