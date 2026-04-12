package de.tum.cit.aet.helios.github.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeploymentReconciliationServiceTest {

  @Mock
  private DeploymentRepository deploymentRepository;

  @Mock
  private HeliosDeploymentRepository heliosDeploymentRepository;

  @Mock
  private GitHubService gitHubService;

  @InjectMocks
  private DeploymentReconciliationService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "enabled", true);
  }

  @Test
  void reconcileStaleDeploymentsUpdatesDeploymentAndSyncsHeliosDeployment() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(43L);
    repository.setNameWithOwner("owner/repo");

    Deployment deployment = new Deployment();
    deployment.setId(501L);
    deployment.setRepository(repository);
    deployment.setState(Deployment.State.IN_PROGRESS);
    deployment.setUpdatedAt(OffsetDateTime.now().minusHours(2));

    HeliosDeployment heliosDeployment = new HeliosDeployment();
    heliosDeployment.setId(601L);
    heliosDeployment.setDeploymentId(501L);
    heliosDeployment.setStatus(HeliosDeployment.Status.IN_PROGRESS);
    heliosDeployment.setUpdatedAt(OffsetDateTime.now().minusHours(3));

    OffsetDateTime remoteUpdatedAt = OffsetDateTime.now();

    when(
            deploymentRepository.findStaleIncompleteDeployments(
                any(), anyList(), any(), anyLong(), any(Pageable.class)))
        .thenReturn(List.of(deployment), List.of());
    when(heliosDeploymentRepository.findByDeploymentId(501L))
        .thenReturn(Optional.of(heliosDeployment));
    when(gitHubService.getLatestDeploymentState("owner/repo", 501L))
        .thenReturn(Optional.of(new GitHubService.DeploymentState("success", remoteUpdatedAt)));

    service.reconcileStaleDeployments();

    assertEquals(Deployment.State.SUCCESS, deployment.getState());
    assertEquals(remoteUpdatedAt, deployment.getUpdatedAt());
    assertEquals(HeliosDeployment.Status.DEPLOYMENT_SUCCESS, heliosDeployment.getStatus());
    assertEquals(remoteUpdatedAt, heliosDeployment.getUpdatedAt());
    verify(deploymentRepository).save(deployment);
    verify(heliosDeploymentRepository).save(heliosDeployment);
    verify(deploymentRepository, times(2))
        .findStaleIncompleteDeployments(any(), anyList(), any(), anyLong(), any(Pageable.class));
  }

  @Test
  void reconcileStaleDeploymentsContinuesToLaterPagesWhenFirstPageHasUnresolvableRows()
      throws Exception {
    Deployment unresolvableDeployment = new Deployment();
    unresolvableDeployment.setId(900L);
    unresolvableDeployment.setRepository(null);
    unresolvableDeployment.setState(Deployment.State.IN_PROGRESS);

    GitRepository repository = new GitRepository();
    repository.setRepositoryId(44L);
    repository.setNameWithOwner("owner/repo");

    Deployment reconcilableDeployment = new Deployment();
    reconcilableDeployment.setId(901L);
    reconcilableDeployment.setRepository(repository);
    reconcilableDeployment.setState(Deployment.State.IN_PROGRESS);
    reconcilableDeployment.setUpdatedAt(OffsetDateTime.now().minusHours(2));

    OffsetDateTime remoteUpdatedAt = OffsetDateTime.now();

    when(
            deploymentRepository.findStaleIncompleteDeployments(
                any(), anyList(), any(), anyLong(), any(Pageable.class)))
        .thenReturn(List.of(unresolvableDeployment), List.of(reconcilableDeployment), List.of());
    when(gitHubService.getLatestDeploymentState("owner/repo", 901L))
        .thenReturn(Optional.of(new GitHubService.DeploymentState("success", remoteUpdatedAt)));
    when(heliosDeploymentRepository.findByDeploymentId(anyLong())).thenReturn(Optional.empty());

    service.reconcileStaleDeployments();

    assertEquals(Deployment.State.SUCCESS, reconcilableDeployment.getState());
    assertEquals(remoteUpdatedAt, reconcilableDeployment.getUpdatedAt());
    verify(deploymentRepository).save(reconcilableDeployment);
    verify(deploymentRepository, times(3))
        .findStaleIncompleteDeployments(any(), anyList(), any(), anyLong(), any(Pageable.class));
  }

  @Test
  void reconcileStaleDeploymentsUsesCursorPaginationNotOffsetPagination() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(45L);
    repository.setNameWithOwner("owner/repo");

    OffsetDateTime firstTs = OffsetDateTime.now().minusHours(4);
    Deployment first = new Deployment();
    first.setId(1001L);
    first.setRepository(repository);
    first.setState(Deployment.State.IN_PROGRESS);
    first.setUpdatedAt(firstTs);

    OffsetDateTime secondTs = OffsetDateTime.now().minusHours(3);
    Deployment second = new Deployment();
    second.setId(1002L);
    second.setRepository(repository);
    second.setState(Deployment.State.IN_PROGRESS);
    second.setUpdatedAt(secondTs);

    when(deploymentRepository.findStaleIncompleteDeployments(
        any(), anyList(), any(), anyLong(), any(Pageable.class)))
        .thenReturn(List.of(first), List.of(second), List.of());
    when(gitHubService.getLatestDeploymentState("owner/repo", 1001L))
        .thenReturn(
            Optional.of(new GitHubService.DeploymentState("success", firstTs.plusMinutes(1))));
    when(gitHubService.getLatestDeploymentState("owner/repo", 1002L))
        .thenReturn(
            Optional.of(new GitHubService.DeploymentState("success", secondTs.plusMinutes(1))));
    when(heliosDeploymentRepository.findByDeploymentId(anyLong())).thenReturn(Optional.empty());

    service.reconcileStaleDeployments();

    ArgumentCaptor<OffsetDateTime> cursorTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    ArgumentCaptor<Long> cursorIdCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

    verify(deploymentRepository, times(3))
        .findStaleIncompleteDeployments(
            any(), anyList(), cursorTimeCaptor.capture(), cursorIdCaptor.capture(),
            pageableCaptor.capture());

    List<OffsetDateTime> cursorTimes = cursorTimeCaptor.getAllValues();
    assertNull(cursorTimes.get(0));
    assertEquals(firstTs, cursorTimes.get(1));
    assertEquals(secondTs, cursorTimes.get(2));

    List<Long> cursorIds = cursorIdCaptor.getAllValues();
    assertEquals(0L, cursorIds.get(0));
    assertEquals(1001L, cursorIds.get(1));
    assertEquals(1002L, cursorIds.get(2));

    List<Pageable> pageableList = pageableCaptor.getAllValues();
    assertEquals(0, pageableList.get(0).getPageNumber());
    assertEquals(0, pageableList.get(1).getPageNumber());
    assertEquals(0, pageableList.get(2).getPageNumber());
  }

  @Test
  void reconcileStaleDeploymentsDoesNothingWhenDisabled() {
    ReflectionTestUtils.setField(service, "enabled", false);

    service.reconcileStaleDeployments();

    verifyNoInteractions(deploymentRepository, heliosDeploymentRepository, gitHubService);
  }
}
