package de.tum.cit.aet.helios.ai.testfailure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.config.GitHubJwtAuthenticationConverter;
import de.tum.cit.aet.helios.config.SecurityConfig;
import de.tum.cit.aet.helios.error.GlobalExceptionHandler;
import de.tum.cit.aet.helios.filters.RepoSecretFilter;
import de.tum.cit.aet.helios.filters.RepositoryInterceptor;
import jakarta.servlet.FilterChain;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@ContextConfiguration(classes = TestFailureAnalysisController.class)
@WebMvcTest(TestFailureAnalysisController.class)
class TestFailureAnalysisControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TestFailureAnalysisService analysisService;
  @MockitoBean private GitHubJwtAuthenticationConverter gitHubJwtAuthenticationConverter;
  @MockitoBean private RepoSecretFilter repoSecretFilter;
  @MockitoBean private RepositoryInterceptor repositoryInterceptor;

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
  void analyzeFailedTestReturnsUnauthorizedForUnauthenticatedUser() throws Exception {
    mockMvc
        .perform(
            post("/api/repositories/1/test-cases/2/failure-analysis").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void analyzeFailedTestReturnsForbiddenForUserWithoutWritePermission() throws Exception {
    mockMvc
        .perform(
            post("/api/repositories/1/test-cases/2/failure-analysis")
                .with(csrf())
                .with(user("reader")))
        .andExpect(status().isForbidden());
  }

  @Test
  void analyzeFailedTestReturnsOkForUserWithWritePermission() throws Exception {
    when(analysisService.analyzeTestFailure(eq(1L), eq(2L), eq(false)))
        .thenReturn(
            new TestFailureAnalysisResponseDto(
                1L,
                TestFailureAnalysisStatus.COMPLETED,
                new TestFailureAnalysisResultDto(
                    "summary",
                    List.of("hypothesis"),
                    List.of("evidence"),
                    List.of("fix"),
                    0.8,
                    "openai",
                    "gpt-4o-mini"),
                null,
                OffsetDateTime.parse("2026-04-16T10:15:30Z"),
                321L,
                false));

    mockMvc
        .perform(
            post("/api/repositories/1/test-cases/2/failure-analysis")
                .with(csrf())
                .with(user("writer").roles("WRITE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repositoryId").value(1))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.result.summary").value("summary"))
        .andExpect(jsonPath("$.durationMs").value(321))
        .andExpect(jsonPath("$.cacheHit").value(false));
  }

  @Test
  void analyzeFailedTestPassesRegenerateQueryToService() throws Exception {
    when(analysisService.analyzeTestFailure(eq(1L), eq(2L), eq(true)))
        .thenReturn(
            new TestFailureAnalysisResponseDto(
                1L,
                TestFailureAnalysisStatus.COMPLETED,
                new TestFailureAnalysisResultDto(
                    "summary",
                    List.of("hypothesis"),
                    List.of("evidence"),
                    List.of("fix"),
                    0.8,
                    "openai",
                    "gpt-4o-mini"),
                null,
                OffsetDateTime.parse("2026-04-16T10:15:30Z"),
                321L,
                false));

    mockMvc
        .perform(
            post("/api/repositories/1/test-cases/2/failure-analysis")
                .queryParam("regenerate", "true")
                .with(csrf())
                .with(user("writer").roles("WRITE")))
        .andExpect(status().isOk());

    verify(analysisService).analyzeTestFailure(1L, 2L, true);
  }
}
