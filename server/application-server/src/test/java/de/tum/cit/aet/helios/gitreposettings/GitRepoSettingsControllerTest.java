package de.tum.cit.aet.helios.gitreposettings;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfigDto;
import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfigService;
import de.tum.cit.aet.helios.error.GlobalExceptionHandler;
import de.tum.cit.aet.helios.gitreposettings.secret.RepoSecretService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ContextConfiguration(classes = GitRepoSettingsController.class)
@WebMvcTest(GitRepoSettingsController.class)
class GitRepoSettingsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WorkflowGroupService workflowGroupService;

  @MockitoBean private GitRepoSettingsService gitRepoSettingsService;

  @MockitoBean private RepoSecretService repoSecretService;

  @MockitoBean private DeploymentWorkflowConfigService deploymentWorkflowConfigService;

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
}
