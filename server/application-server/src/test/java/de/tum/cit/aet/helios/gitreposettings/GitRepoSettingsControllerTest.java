package de.tum.cit.aet.helios.gitreposettings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.config.GitHubJwtAuthenticationConverter;
import de.tum.cit.aet.helios.config.SecurityConfig;
import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfigDto;
import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfigService;
import de.tum.cit.aet.helios.error.GlobalExceptionHandler;
import de.tum.cit.aet.helios.filters.RepoSecretFilter;
import de.tum.cit.aet.helios.filters.RepositoryInterceptor;
import de.tum.cit.aet.helios.gitreposettings.secret.RepoSecretService;
import de.tum.cit.aet.helios.workflow.detection.WorkflowDeploymentJobDetectionDto;
import de.tum.cit.aet.helios.workflow.detection.WorkflowDeploymentJobDetectionService;
import jakarta.servlet.FilterChain;
import java.util.Optional;
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
@ContextConfiguration(classes = GitRepoSettingsController.class)
@WebMvcTest(GitRepoSettingsController.class)
class GitRepoSettingsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WorkflowGroupService workflowGroupService;
  @MockitoBean private GitRepoSettingsService gitRepoSettingsService;
  @MockitoBean private RepoSecretService repoSecretService;
  @MockitoBean private DeploymentWorkflowConfigService deploymentWorkflowConfigService;
  @MockitoBean private WorkflowDeploymentJobDetectionService workflowDeploymentJobDetectionService;

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
  void getDeploymentWorkflowConfigReturnsConfig() throws Exception {
    DeploymentWorkflowConfigDto dto = new DeploymentWorkflowConfigDto(7L, "deploy");
    when(deploymentWorkflowConfigService.findByWorkflowId(1L, 7L)).thenReturn(Optional.of(dto));

    mockMvc
        .perform(
            get("/api/settings/{repositoryId}/workflows/{workflowId}/deployment-config", 1L, 7L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowId").value(7L))
        .andExpect(jsonPath("$.deployJobName").value("deploy"));
  }

  @Test
  void getDeploymentWorkflowConfigReturnsNotFoundWhenConfigIsMissing() throws Exception {
    when(deploymentWorkflowConfigService.findByWorkflowId(1L, 7L)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/api/settings/{repositoryId}/workflows/{workflowId}/deployment-config", 1L, 7L))
        .andExpect(status().isNotFound());
  }

  @Test
  void getDeploymentWorkflowConfigReturnsBadRequestForWorkflowInAnotherRepository()
      throws Exception {
    when(deploymentWorkflowConfigService.findByWorkflowId(1L, 7L))
        .thenThrow(new IllegalArgumentException("Workflow 7 does not belong to repository 1"));

    mockMvc
        .perform(
            get("/api/settings/{repositoryId}/workflows/{workflowId}/deployment-config", 1L, 7L))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message").value("Error: Workflow 7 does not belong to repository 1"));
  }

  @Test
  void getDeploymentWorkflowConfigReturnsBadRequestWhenWorkflowDoesNotExist() throws Exception {
    when(deploymentWorkflowConfigService.findByWorkflowId(1L, 9L))
        .thenThrow(new IllegalArgumentException("Workflow not found: 9"));

    mockMvc
        .perform(
            get("/api/settings/{repositoryId}/workflows/{workflowId}/deployment-config", 1L, 9L))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Error: Workflow not found: 9"));
  }

  @Test
  void detectDeploymentJobReturnsUnauthorizedForUnauthenticatedUser() throws Exception {
    mockMvc
        .perform(
            post(
                    "/api/settings/{repositoryId}/workflows/{workflowId}/detect-deployment-job",
                    1L,
                    2L)
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void detectDeploymentJobReturnsForbiddenForNonMaintainer() throws Exception {
    mockMvc
        .perform(
            post(
                    "/api/settings/{repositoryId}/workflows/{workflowId}/detect-deployment-job",
                    1L,
                    2L)
                .with(csrf())
                .with(user("developer").roles("WRITE")))
        .andExpect(status().isForbidden());
  }

  @Test
  void detectDeploymentJobReturnsResultForMaintainer() throws Exception {
    when(workflowDeploymentJobDetectionService.detectDeploymentJob(1L, 2L))
        .thenReturn(
            new WorkflowDeploymentJobDetectionDto(
                2L,
                ".github/workflows/deploy.yml",
                "main",
                "Deploy to staging",
                WorkflowDeploymentJobDetectionDto.Status.FOUND,
                "Detected deployment job successfully."));

    mockMvc
        .perform(
            post(
                    "/api/settings/{repositoryId}/workflows/{workflowId}/detect-deployment-job",
                    1L,
                    2L)
                .with(csrf())
                .with(user("maintainer").roles("MAINTAINER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowId").value(2))
        .andExpect(jsonPath("$.workflowPath").value(".github/workflows/deploy.yml"))
        .andExpect(jsonPath("$.ref").value("main"))
        .andExpect(jsonPath("$.deploymentJobName").value("Deploy to staging"))
        .andExpect(jsonPath("$.status").value("FOUND"));
  }
}
