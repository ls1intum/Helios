package de.tum.cit.aet.helios.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeliosDeploymentWorkflowRunSyncServiceTest {

  @Mock private GitHubService gitHubService;
  @Mock private HeliosDeploymentRepository heliosDeploymentRepository;
  @Mock private DeploymentRepository deploymentRepository;

  @InjectMocks private HeliosDeploymentWorkflowRunSyncService syncService;

  @Test
  void synchronizeTerminalStateFromWorkflowRunUpdatesHeliosAndLinkedDeployment()
      throws Exception {
    OffsetDateTime remoteUpdatedAt = OffsetDateTime.now();
    HeliosDeployment heliosDeployment = new HeliosDeployment();
    heliosDeployment.setId(1L);
    heliosDeployment.setWorkflowRunId(123L);
    heliosDeployment.setDeploymentId(501L);
    heliosDeployment.setStatus(HeliosDeployment.Status.IN_PROGRESS);
    heliosDeployment.setUpdatedAt(remoteUpdatedAt.minusMinutes(5));

    Deployment deployment = new Deployment();
    deployment.setId(501L);
    deployment.setState(Deployment.State.IN_PROGRESS);
    deployment.setUpdatedAt(remoteUpdatedAt.minusMinutes(5));

    when(gitHubService.getWorkflowRunState("owner/repo", 123L))
        .thenReturn(
            Optional.of(
                new GitHubService.WorkflowRunState("completed", "success", remoteUpdatedAt)));
    when(heliosDeploymentRepository.findByWorkflowRunId(123L))
        .thenReturn(Optional.of(heliosDeployment));
    when(deploymentRepository.findById(501L)).thenReturn(Optional.of(deployment));

    assertTrue(syncService.synchronizeTerminalStateFromWorkflowRun("owner/repo", 123L));

    assertEquals(HeliosDeployment.Status.DEPLOYMENT_SUCCESS, heliosDeployment.getStatus());
    assertEquals(Deployment.State.SUCCESS, deployment.getState());
    assertEquals(remoteUpdatedAt, heliosDeployment.getUpdatedAt());
    assertEquals(remoteUpdatedAt, deployment.getUpdatedAt());
    verify(heliosDeploymentRepository).save(heliosDeployment);
    verify(deploymentRepository).save(deployment);
  }

  @Test
  void synchronizeTerminalStateFromWorkflowRunSkipsNonTerminalRuns() throws Exception {
    when(gitHubService.getWorkflowRunState("owner/repo", 123L))
        .thenReturn(
            Optional.of(
                new GitHubService.WorkflowRunState(
                    "in_progress", null, OffsetDateTime.now())));

    assertFalse(syncService.synchronizeTerminalStateFromWorkflowRun("owner/repo", 123L));

    verify(heliosDeploymentRepository, never()).findByWorkflowRunId(123L);
  }
}
