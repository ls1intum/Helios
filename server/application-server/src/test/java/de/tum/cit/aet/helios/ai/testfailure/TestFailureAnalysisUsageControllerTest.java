package de.tum.cit.aet.helios.ai.testfailure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.config.GitHubJwtAuthenticationConverter;
import de.tum.cit.aet.helios.config.SecurityConfig;
import de.tum.cit.aet.helios.error.GlobalExceptionHandler;
import de.tum.cit.aet.helios.filters.RepoSecretFilter;
import de.tum.cit.aet.helios.filters.RepositoryInterceptor;
import jakarta.servlet.FilterChain;
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
@ContextConfiguration(classes = TestFailureAnalysisUsageController.class)
@WebMvcTest(TestFailureAnalysisUsageController.class)
class TestFailureAnalysisUsageControllerTest {

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
  void getFailureAnalysisUsageReturnsOkForUserWithWritePermission() throws Exception {
    when(analysisService.getUsage())
        .thenReturn(
            new TestFailureAnalysisUsageDto(
                true,
                5,
                20,
                2,
                5,
                600L));

    mockMvc
        .perform(
            get("/api/test-failure-analysis/usage")
                .with(user("writer").roles("WRITE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dailyUsed").value(5))
        .andExpect(jsonPath("$.dailyLimit").value(20))
        .andExpect(jsonPath("$.burstUsed").value(2))
        .andExpect(jsonPath("$.burstLimit").value(5));
  }
}
