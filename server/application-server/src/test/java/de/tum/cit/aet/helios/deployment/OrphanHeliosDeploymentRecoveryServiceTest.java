package de.tum.cit.aet.helios.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrphanHeliosDeploymentRecoveryServiceTest {

  @Mock
  private HeliosDeploymentRepository heliosDeploymentRepository;

  @Mock
  private HeliosDeploymentWorkflowRunSyncService heliosDeploymentWorkflowRunSyncService;

  @InjectMocks
  private OrphanHeliosDeploymentRecoveryService deploymentRecoveryService;

  @Test
  void markStuckOrphanDeploymentsMarksStaleEntriesAsFailed() {
    HeliosDeployment stuck1 = createStuckHeliosDeployment(1L, HeliosDeployment.Status.IN_PROGRESS);
    HeliosDeployment stuck2 = createStuckHeliosDeployment(2L, HeliosDeployment.Status.WAITING);
    HeliosDeployment notStuck = createStuckHeliosDeployment(3L, HeliosDeployment.Status.QUEUED);
    notStuck.setStatusUpdatedAt(OffsetDateTime.now().minusMinutes(30));

    when(heliosDeploymentRepository.findStuckDeploymentsWithWorkflowRunId(any()))
        .thenReturn(List.of());
    when(heliosDeploymentRepository.findStuckDeploymentsWithoutDeploymentIdAndWorkflowRunIdIsNull(
            any()))
        .thenReturn(List.of(stuck1, stuck2));
    when(heliosDeploymentRepository.save(any(HeliosDeployment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    deploymentRecoveryService.markStuckOrphanHeliosDeploymentsAsFailure();

    // Assert
    verify(heliosDeploymentRepository, times(1))
        .findStuckDeploymentsWithWorkflowRunId(any(OffsetDateTime.class));
    verify(heliosDeploymentRepository, times(1))
        .findStuckDeploymentsWithoutDeploymentIdAndWorkflowRunIdIsNull(
            any(OffsetDateTime.class));
    verify(heliosDeploymentRepository, times(2)).save(any(HeliosDeployment.class));
    assertEquals(HeliosDeployment.Status.FAILED, stuck1.getStatus());
    assertEquals(HeliosDeployment.Status.FAILED, stuck2.getStatus());
    assertEquals(HeliosDeployment.Status.QUEUED, notStuck.getStatus());
  }

  @Test
  void synchronizesWorkflowBackedDeploymentsAndForceFailsOrphansWhenSyncMissesTerminalState()
      throws IOException {
    HeliosDeployment synced =
        createWorkflowBackedHeliosDeployment(10L, 1001L, true, "owner/synced");
    HeliosDeployment orphanFallback =
        createWorkflowBackedHeliosDeployment(11L, 1002L, true, "owner/orphan");
    HeliosDeployment linkedNoSync =
        createWorkflowBackedHeliosDeployment(12L, 1003L, false, "owner/linked");

    when(heliosDeploymentRepository.findStuckDeploymentsWithWorkflowRunId(any()))
        .thenReturn(List.of(synced, orphanFallback, linkedNoSync));
    when(heliosDeploymentRepository.findStuckDeploymentsWithoutDeploymentIdAndWorkflowRunIdIsNull(
            any()))
        .thenReturn(List.of());
    when(heliosDeploymentWorkflowRunSyncService.synchronizeTerminalStateFromWorkflowRun(
            eq("owner/synced"), eq(1001L)))
        .thenReturn(true);
    when(heliosDeploymentWorkflowRunSyncService.synchronizeTerminalStateFromWorkflowRun(
            eq("owner/orphan"), eq(1002L)))
        .thenReturn(false);
    when(heliosDeploymentWorkflowRunSyncService.synchronizeTerminalStateFromWorkflowRun(
            eq("owner/linked"), eq(1003L)))
        .thenReturn(false);
    when(heliosDeploymentRepository.save(any(HeliosDeployment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    deploymentRecoveryService.markStuckOrphanHeliosDeploymentsAsFailure();

    // Orphan with no terminal state from GitHub falls back to FAILED.
    assertEquals(HeliosDeployment.Status.FAILED, orphanFallback.getStatus());
    // Linked deployment (deploymentId != null) is left alone — old code never touched these.
    assertEquals(HeliosDeployment.Status.IN_PROGRESS, linkedNoSync.getStatus());
    // Synced row is updated by the sync service (mocked here), so its status is unchanged
    // in this assertion — what matters is that we did NOT also force-fail it.
    assertEquals(HeliosDeployment.Status.IN_PROGRESS, synced.getStatus());

    // Only the orphan fallback save should hit the repository.
    verify(heliosDeploymentRepository, times(1)).save(any(HeliosDeployment.class));
  }

  @Test
  void synchronizesAllAndForceFailsNoneWhenSyncCoversEveryStuckDeployment() throws IOException {
    HeliosDeployment stuck =
        createWorkflowBackedHeliosDeployment(20L, 2001L, true, "owner/repo");

    when(heliosDeploymentRepository.findStuckDeploymentsWithWorkflowRunId(any()))
        .thenReturn(List.of(stuck));
    when(heliosDeploymentRepository.findStuckDeploymentsWithoutDeploymentIdAndWorkflowRunIdIsNull(
            any()))
        .thenReturn(List.of());
    when(heliosDeploymentWorkflowRunSyncService.synchronizeTerminalStateFromWorkflowRun(
            anyString(), anyLong()))
        .thenReturn(true);

    deploymentRecoveryService.markStuckOrphanHeliosDeploymentsAsFailure();

    // Sync handled it, so no force-fail save should happen here.
    verify(heliosDeploymentRepository, never()).save(any(HeliosDeployment.class));
    assertEquals(HeliosDeployment.Status.IN_PROGRESS, stuck.getStatus());
  }

  @Test
  void forceFailsOrphanWorkflowBackedDeploymentWhenSyncThrowsIoException() throws IOException {
    HeliosDeployment orphan =
        createWorkflowBackedHeliosDeployment(30L, 3001L, true, "owner/repo");

    when(heliosDeploymentRepository.findStuckDeploymentsWithWorkflowRunId(any()))
        .thenReturn(List.of(orphan));
    when(heliosDeploymentRepository.findStuckDeploymentsWithoutDeploymentIdAndWorkflowRunIdIsNull(
            any()))
        .thenReturn(List.of());
    when(heliosDeploymentWorkflowRunSyncService.synchronizeTerminalStateFromWorkflowRun(
            anyString(), anyLong()))
        .thenThrow(new IOException("github down"));
    when(heliosDeploymentRepository.save(any(HeliosDeployment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    deploymentRecoveryService.markStuckOrphanHeliosDeploymentsAsFailure();

    assertEquals(HeliosDeployment.Status.FAILED, orphan.getStatus());
    verify(heliosDeploymentRepository, times(1)).save(any(HeliosDeployment.class));
  }

  @Test
  void markStuckOrphanDeploymentsSkipsWhenNoneFound() {
    when(heliosDeploymentRepository.findStuckDeploymentsWithWorkflowRunId(any()))
        .thenReturn(List.of());
    when(heliosDeploymentRepository.findStuckDeploymentsWithoutDeploymentIdAndWorkflowRunIdIsNull(
            any()))
        .thenReturn(List.of());

    deploymentRecoveryService.markStuckOrphanHeliosDeploymentsAsFailure();

    // Assert
    verify(heliosDeploymentRepository, times(1))
        .findStuckDeploymentsWithWorkflowRunId(any(OffsetDateTime.class));
    verify(heliosDeploymentRepository, times(1))
        .findStuckDeploymentsWithoutDeploymentIdAndWorkflowRunIdIsNull(
            any(OffsetDateTime.class));
    verify(heliosDeploymentRepository, never()).save(any(HeliosDeployment.class));
  }

  private HeliosDeployment createStuckHeliosDeployment(Long id, HeliosDeployment.Status status) {
    HeliosDeployment deployment = new HeliosDeployment();
    deployment.setId(id);
    deployment.setDeploymentId(null);
    deployment.setStatus(status);
    deployment.setStatusUpdatedAt(OffsetDateTime.now().minusHours(3));
    deployment.setUpdatedAt(OffsetDateTime.now().minusHours(3));
    deployment.setCreatedAt(OffsetDateTime.now().minusHours(3));
    return deployment;
  }

  private HeliosDeployment createWorkflowBackedHeliosDeployment(
      Long id, Long workflowRunId, boolean orphan, String repositoryNameWithOwner) {
    HeliosDeployment deployment =
        createStuckHeliosDeployment(id, HeliosDeployment.Status.IN_PROGRESS);
    deployment.setWorkflowRunId(workflowRunId);
    if (!orphan) {
      deployment.setDeploymentId(id + 5000L);
    }

    GitRepository repository = new GitRepository();
    repository.setNameWithOwner(repositoryNameWithOwner);
    Environment environment = new Environment();
    environment.setRepository(repository);
    deployment.setEnvironment(environment);

    return deployment;
  }
}
