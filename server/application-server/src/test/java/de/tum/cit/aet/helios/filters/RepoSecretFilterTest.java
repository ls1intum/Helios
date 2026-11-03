package de.tum.cit.aet.helios.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsRepository;
import de.tum.cit.aet.helios.gitreposettings.secret.RepoSecretService;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RepoSecretFilterTest {

  private static final String TOKEN = "repo-7-" + "a".repeat(43);

  private final GitRepoSettingsRepository settingsRepository = mock(GitRepoSettingsRepository.class);
  private final RepoSecretService secrets = mock(RepoSecretService.class);
  private final RepoSecretFilter filter = new RepoSecretFilter(settingsRepository, secrets);

  @Test
  void onlyEnvironmentStatusPathsAreFiltered() {
    assertFalse(filter.shouldNotFilter(request("/api/environments/status/1")));
    assertTrue(filter.shouldNotFilter(request("/api/environments")));
    assertTrue(filter.shouldNotFilter(request("/api/workflows/runs")));
    // The removed flaky-test endpoint must no longer be guarded by the shared-secret filter.
    assertTrue(filter.shouldNotFilter(request("/api/tests/flakiness-scores")));
  }

  @Test
  void missingAuthorizationHeaderIsRejected() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request("/api/environments/status/1"), response, chain);

    assertEquals(401, response.getStatus());
    verifyNoInteractions(chain, settingsRepository);
  }

  @Test
  void malformedTokenIsRejected() throws Exception {
    MockHttpServletRequest request = request("/api/environments/status/1");
    request.addHeader("Authorization", "Secret not-a-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    assertEquals(401, response.getStatus());
    verifyNoInteractions(chain, settingsRepository);
  }

  @Test
  void invalidSecretIsRejected() throws Exception {
    GitRepoSettings settings = new GitRepoSettings();
    when(settingsRepository.findByRepositoryRepositoryId(7L)).thenReturn(Optional.of(settings));
    when(secrets.matches(settings, TOKEN)).thenReturn(false);
    MockHttpServletRequest request = request("/api/environments/status/1");
    request.addHeader("Authorization", "Secret " + TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    assertEquals(401, response.getStatus());
    verifyNoInteractions(chain);
  }

  @Test
  void validSecretContinuesTheChainAndExposesSettings() throws Exception {
    GitRepoSettings settings = new GitRepoSettings();
    when(settingsRepository.findByRepositoryRepositoryId(7L)).thenReturn(Optional.of(settings));
    when(secrets.matches(settings, TOKEN)).thenReturn(true);
    MockHttpServletRequest request = request("/api/environments/status/1");
    request.addHeader("Authorization", "Secret " + TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertSame(settings, request.getAttribute("repository"));
  }

  private static MockHttpServletRequest request(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setRequestURI(uri);
    return request;
  }
}
