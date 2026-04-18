package de.tum.cit.aet.helios.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeploymentWorkflowConfigServiceTest {

  @Mock private DeploymentWorkflowConfigRepository configRepository;

  @Mock private WorkflowRepository workflowRepository;

  @InjectMocks private DeploymentWorkflowConfigService deploymentWorkflowConfigService;

  @Test
  void findByWorkflowIdReturnsConfigWhenWorkflowBelongsToRepository() {
    Workflow workflow = createWorkflow(7L, 1L);
    DeploymentWorkflowConfig config = new DeploymentWorkflowConfig();
    config.setWorkflow(workflow);
    config.setDeployJobName("deploy");

    when(workflowRepository.findById(7L)).thenReturn(Optional.of(workflow));
    when(configRepository.findByWorkflow(workflow)).thenReturn(Optional.of(config));

    Optional<DeploymentWorkflowConfigDto> result =
        deploymentWorkflowConfigService.findByWorkflowId(1L, 7L);

    assertTrue(result.isPresent());
    assertEquals(new DeploymentWorkflowConfigDto(7L, "deploy"), result.get());
    verify(configRepository).findByWorkflow(workflow);
  }

  @Test
  void findByWorkflowIdReturnsEmptyWhenWorkflowHasNoConfig() {
    Workflow workflow = createWorkflow(7L, 1L);

    when(workflowRepository.findById(7L)).thenReturn(Optional.of(workflow));
    when(configRepository.findByWorkflow(workflow)).thenReturn(Optional.empty());

    Optional<DeploymentWorkflowConfigDto> result =
        deploymentWorkflowConfigService.findByWorkflowId(1L, 7L);

    assertTrue(result.isEmpty());
    verify(configRepository).findByWorkflow(workflow);
  }

  @Test
  void findByWorkflowIdThrowsWhenWorkflowDoesNotExist() {
    when(workflowRepository.findById(7L)).thenReturn(Optional.empty());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> deploymentWorkflowConfigService.findByWorkflowId(1L, 7L));

    assertEquals("Workflow not found: 7", exception.getMessage());
  }

  @Test
  void findByWorkflowIdThrowsWhenWorkflowBelongsToAnotherRepository() {
    Workflow workflow = createWorkflow(7L, 2L);

    when(workflowRepository.findById(7L)).thenReturn(Optional.of(workflow));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> deploymentWorkflowConfigService.findByWorkflowId(1L, 7L));

    assertEquals("Workflow 7 does not belong to repository 1", exception.getMessage());
  }

  private Workflow createWorkflow(Long workflowId, Long repositoryId) {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(repositoryId);

    Workflow workflow = new Workflow();
    workflow.setId(workflowId);
    workflow.setRepository(repository);
    return workflow;
  }
}
