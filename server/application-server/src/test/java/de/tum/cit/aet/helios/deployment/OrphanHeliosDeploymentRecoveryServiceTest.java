package de.tum.cit.aet.helios.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
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
}
