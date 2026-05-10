package de.tum.cit.aet.helios.deployment.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.ws.EnvironmentDeploymentWebSocketPublisher;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubDeploymentSyncServiceTest {

  @Mock private DeploymentRepository deploymentRepository;
  @Mock private PullRequestRepository pullRequestRepository;
  @Mock private DeploymentConverter deploymentConverter;
  @Mock private HeliosDeploymentRepository heliosDeploymentRepository;
  @Mock private EnvironmentDeploymentWebSocketPublisher environmentDeploymentWebSocketPublisher;

  @InjectMocks private GitHubDeploymentSyncService gitHubDeploymentSyncService;

  @Test
  void processDeploymentUpdatesHeliosStatusWhenDeploymentTurnsInProgress() {
    final OffsetDateTime deploymentUpdatedAt = OffsetDateTime.parse("2026-04-23T10:15:00Z");

    GitRepository repository = new GitRepository();
    repository.setRepositoryId(42L);

    Environment environment = new Environment();
    environment.setName("test");

    HeliosDeployment heliosDeployment = new HeliosDeployment();
    heliosDeployment.setEnvironment(environment);
    heliosDeployment.setBranchName("main");
    heliosDeployment.setUpdatedAt(deploymentUpdatedAt.minusMinutes(1));

    DeploymentSource deploymentSource = mock(DeploymentSource.class);
    when(deploymentSource.getId()).thenReturn(99L);
    when(deploymentRepository.findById(99L)).thenReturn(Optional.empty());
    doAnswer(invocation -> {
      Deployment deployment = invocation.getArgument(1);
      deployment.setId(99L);
      deployment.setRef("main");
      deployment.setSha("abc123");
      deployment.setState(Deployment.State.IN_PROGRESS);
      deployment.setUpdatedAt(deploymentUpdatedAt);
      return deployment;
    }).when(deploymentConverter).update(any(DeploymentSource.class), any(Deployment.class));
    when(pullRequestRepository.findOpenPrByBranchNameOrSha(42L, "main", "abc123"))
        .thenReturn(Optional.empty());
    when(
            heliosDeploymentRepository
                .findTopByEnvironmentAndBranchNameOrderByCreatedAtDesc(environment, "main"))
        .thenReturn(Optional.of(heliosDeployment));

    gitHubDeploymentSyncService.processDeployment(
        deploymentSource, repository, environment, null);

    assertEquals(HeliosDeployment.Status.IN_PROGRESS, heliosDeployment.getStatus());
    assertEquals(deploymentUpdatedAt, heliosDeployment.getUpdatedAt());
    verify(heliosDeploymentRepository).save(heliosDeployment);
    verify(environmentDeploymentWebSocketPublisher).publishAfterCommit(environment);
  }
}
