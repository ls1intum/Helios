package de.tum.cit.aet.helios.github.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

    when(deploymentRepository.findStaleIncompleteDeployments(any(), anyList(), any(Pageable.class)))
        .thenReturn(List.of(deployment));
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
  }

  @Test
  void reconcileStaleDeploymentsDoesNothingWhenDisabled() {
    ReflectionTestUtils.setField(service, "enabled", false);

    service.reconcileStaleDeployments();

    verifyNoInteractions(deploymentRepository, heliosDeploymentRepository, gitHubService);
  }
}
