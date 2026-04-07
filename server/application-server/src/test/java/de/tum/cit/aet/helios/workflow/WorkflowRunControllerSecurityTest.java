package de.tum.cit.aet.helios.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.config.GitHubJwtAuthenticationConverter;
import de.tum.cit.aet.helios.config.SecurityConfig;
import de.tum.cit.aet.helios.error.GlobalExceptionHandler;
import de.tum.cit.aet.helios.filters.RepoSecretFilter;
import de.tum.cit.aet.helios.filters.RepositoryInterceptor;
import de.tum.cit.aet.helios.workflow.logs.WorkflowRunLogReaderService;
import de.tum.cit.aet.helios.workflow.logs.WorkflowRunLogsResponse;
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
@ContextConfiguration(classes = WorkflowRunController.class)
@WebMvcTest(WorkflowRunController.class)
class WorkflowRunControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WorkflowRunService workflowRunService;

  @MockitoBean private WorkflowRunLogReaderService workflowRunLogReaderService;

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

    when(workflowRunLogReaderService.getLogs(42L, false))
        .thenReturn(
            new WorkflowRunLogsResponse(
                42L,
                "deploy",
                "Deploy preview",
                WorkflowRun.Conclusion.SUCCESS,
                "https://github.com/owner/repo/actions/runs/42",
                true,
                OffsetDateTime.parse("2026-03-12T10:15:30Z"),
                0,
                List.of()));
  }

  @Test
  void getWorkflowRunLogsReturnsForbiddenWhenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/workflows/runs/{workflowRunId}/logs", 42L))
        .andExpect(status().isForbidden());
  }

  @Test
  void getWorkflowRunLogsReturnsForbiddenWhenUserLacksWritePermission() throws Exception {
    mockMvc
        .perform(get("/api/workflows/runs/{workflowRunId}/logs", 42L).with(user("reader")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getWorkflowRunLogsReturnsOkWhenUserHasWritePermission() throws Exception {
    mockMvc
        .perform(
            get("/api/workflows/runs/{workflowRunId}/logs", 42L)
                .with(user("writer").roles("WRITE")))
        .andExpect(status().isOk());
  }
}
