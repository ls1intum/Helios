package de.tum.cit.aet.helios.pullrequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.config.GitHubJwtAuthenticationConverter;
import de.tum.cit.aet.helios.config.SecurityConfig;
import de.tum.cit.aet.helios.error.GlobalExceptionHandler;
import de.tum.cit.aet.helios.filters.RepoSecretFilter;
import de.tum.cit.aet.helios.filters.RepositoryInterceptor;
import de.tum.cit.aet.helios.permissions.RepositoryAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@ContextConfiguration(classes = PullRequestController.class)
@WebMvcTest(PullRequestController.class)
class PullRequestControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PullRequestService pullRequestService;

  @MockitoBean private PullRequestStateReconciliationService pullRequestStateReconciliationService;

  @MockitoBean private GitHubJwtAuthenticationConverter gitHubJwtAuthenticationConverter;

  @MockitoBean private RepoSecretFilter repoSecretFilter;

  @MockitoBean private RepositoryInterceptor repositoryInterceptor;

  @MockitoBean private RepositoryAuthorizationService repositoryAuthorizationService;

  @BeforeEach
  void setUp() throws Exception {
    doAnswer(
            invocation -> {
              FilterChain chain = invocation.getArgument(2);
              chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(repoSecretFilter)
        .doFilter(any(), any(), any());
  }

  @Test
  void reconcileStateReturnsResultForAdminUser() throws Exception {
    when(repositoryAuthorizationService.hasAdminAccess(1L)).thenReturn(true);
    when(pullRequestStateReconciliationService.reconcilePullRequestState(1L, true))
        .thenReturn(
            new PullRequestStateReconciliationResultDto(
                true,
                1L,
                "ls1intum/Helios",
                2,
                1,
                List.of(1001L),
                List.of(42),
                1,
                0,
                List.of()));

    mockMvc
        .perform(
            post("/api/pullrequests/repository/{repositoryId}/reconcile-state", 1L)
                .param("dryRun", "true")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(SecurityMockMvcRequestPostProcessors.user("admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dryRun").value(true))
        .andExpect(jsonPath("$.updatedCount").value(1));
  }

  @Test
  void reconcileStateReturnsNotFoundWhenRepositoryDoesNotExist() throws Exception {
    when(repositoryAuthorizationService.hasAdminAccess(99L)).thenReturn(true);
    when(pullRequestStateReconciliationService.reconcilePullRequestState(99L, false))
        .thenThrow(new EntityNotFoundException("Repository not found with ID: 99"));

    mockMvc
        .perform(
            post("/api/pullrequests/repository/{repositoryId}/reconcile-state", 99L)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(SecurityMockMvcRequestPostProcessors.user("admin")))
        .andExpect(status().isNotFound());
  }

  @Test
  void reconcileStateReturnsForbiddenWhenUserHasNoRepositoryAdminAccess() throws Exception {
    when(repositoryAuthorizationService.hasAdminAccess(1L)).thenReturn(false);

    mockMvc
        .perform(
            post("/api/pullrequests/repository/{repositoryId}/reconcile-state", 1L)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(SecurityMockMvcRequestPostProcessors.user("user")))
        .andExpect(status().isForbidden());
  }
}
