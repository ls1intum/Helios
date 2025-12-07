package de.tum.cit.aet.helios.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DeploymentRecoveryServiceTest {

  @Mock
  private DeploymentRepository deploymentRepository;

  @Mock
  private HeliosDeploymentRepository heliosDeploymentRepository;

  @InjectMocks
  private DeploymentRecoveryService deploymentRecoveryService;

  private Deployment stuckDeployment1;
  private Deployment stuckDeployment2;
  private Deployment recentDeployment;
  private HeliosDeployment stuckHeliosDeployment1;
  private HeliosDeployment stuckHeliosDeployment2;
  private HeliosDeployment recentHeliosDeployment;
  private Environment environment;

  @BeforeEach
  void setUp() {
    GitRepository gitRepository = new GitRepository();
    gitRepository.setRepositoryId(1L);

    environment = new Environment();
    environment.setId(1L);
    environment.setName("test-environment");
    environment.setRepository(gitRepository);

    // Create two deployments stuck in IN_PROGRESS for more than an hour
    stuckDeployment1 = new Deployment();
    stuckDeployment1.setId(1L);
    stuckDeployment1.setState(Deployment.State.IN_PROGRESS);
    stuckDeployment1.setUpdatedAt(OffsetDateTime.now().minusHours(2));
    stuckDeployment1.setCreatedAt(OffsetDateTime.now().minusHours(2));
    stuckDeployment1.setEnvironment(environment);

    stuckDeployment2 = new Deployment();
    stuckDeployment2.setId(2L);
    stuckDeployment2.setState(Deployment.State.IN_PROGRESS);
    stuckDeployment2.setUpdatedAt(OffsetDateTime.now().minusHours(3));
    stuckDeployment2.setCreatedAt(OffsetDateTime.now().minusHours(3));
    stuckDeployment2.setEnvironment(environment);

    // Create a recent deployment (should not be marked as FAILED)
    recentDeployment = new Deployment();
    recentDeployment.setId(3L);
    recentDeployment.setState(Deployment.State.IN_PROGRESS);
    recentDeployment.setUpdatedAt(OffsetDateTime.now().minusMinutes(30));
    recentDeployment.setCreatedAt(OffsetDateTime.now().minusMinutes(30));
    recentDeployment.setEnvironment(environment);

    // Create two Helios deployments stuck in IN_PROGRESS for more than an hour
    stuckHeliosDeployment1 = new HeliosDeployment();
    stuckHeliosDeployment1.setId(1L);
    stuckHeliosDeployment1.setStatus(HeliosDeployment.Status.IN_PROGRESS);
    stuckHeliosDeployment1.setStatusUpdatedAt(OffsetDateTime.now().minusHours(2));
    stuckHeliosDeployment1.setUpdatedAt(OffsetDateTime.now().minusHours(2));
    stuckHeliosDeployment1.setCreatedAt(OffsetDateTime.now().minusHours(2));
    stuckHeliosDeployment1.setEnvironment(environment);

    // Create another stuck deployment
    stuckHeliosDeployment2 = new HeliosDeployment();
    stuckHeliosDeployment2.setId(2L);
    stuckHeliosDeployment2.setStatus(HeliosDeployment.Status.IN_PROGRESS);
    stuckHeliosDeployment2.setStatusUpdatedAt(OffsetDateTime.now().minusHours(3));
    stuckHeliosDeployment2.setUpdatedAt(OffsetDateTime.now().minusHours(3));
    stuckHeliosDeployment2.setCreatedAt(OffsetDateTime.now().minusHours(3));
    stuckHeliosDeployment2.setEnvironment(environment);

    // Create a recent deployment (should not be marked as FAILED)
    recentHeliosDeployment = new HeliosDeployment();
    recentHeliosDeployment.setId(3L);
    recentHeliosDeployment.setStatus(HeliosDeployment.Status.IN_PROGRESS);
    recentHeliosDeployment.setStatusUpdatedAt(OffsetDateTime.now().minusMinutes(30));
    recentHeliosDeployment.setUpdatedAt(OffsetDateTime.now().minusMinutes(30));
    recentHeliosDeployment.setCreatedAt(OffsetDateTime.now().minusMinutes(30));
    recentHeliosDeployment.setEnvironment(environment);
  }

  @Test
  void testMarkStuckDeploymentsAsFailureWhenDeploymentsExistAndNoStuckHeliosDeployments() {
    // Arrange
    List<Deployment> stuckDeployments = List.of(stuckDeployment1, stuckDeployment2);

    when(deploymentRepository.findStuckDeployments(any(OffsetDateTime.class)))
        .thenReturn(stuckDeployments);
    when(heliosDeploymentRepository.findStuckDeployments(any(OffsetDateTime.class)))
        .thenReturn(Collections.emptyList());
    when(deploymentRepository.save(any(Deployment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    deploymentRecoveryService.markStuckDeploymentsAsFailure();

    // Assert
    verify(deploymentRepository, times(1))
        .findStuckDeployments(any(OffsetDateTime.class));
    verify(heliosDeploymentRepository, times(1))
        .findStuckDeployments(any(OffsetDateTime.class));
    verify(deploymentRepository,
        times(2)).save(any(Deployment.class));
    verify(heliosDeploymentRepository,
        times(0)).save(any(HeliosDeployment.class));
    assertEquals(Deployment.State.FAILURE, stuckDeployment1.getState());
    assertEquals(Deployment.State.FAILURE, stuckDeployment2.getState());
    assertEquals(Deployment.State.IN_PROGRESS, recentDeployment.getState());
  }

  @Test
  void testMarkStuckHeliosDeploymentsAsFailureWhenHeliosDeploymentsExistAndNoStuckDeployments() {
    // Arrange
    List<HeliosDeployment> stuckDeployments = List.of(stuckHeliosDeployment1,
        stuckHeliosDeployment2);

    when(heliosDeploymentRepository.findStuckDeployments(any(OffsetDateTime.class)))
        .thenReturn(stuckDeployments);
    when(deploymentRepository.findStuckDeployments(any(OffsetDateTime.class)))
        .thenReturn(Collections.emptyList());
    when(heliosDeploymentRepository.save(any(HeliosDeployment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    deploymentRecoveryService.markStuckDeploymentsAsFailure();

    // Assert
    verify(heliosDeploymentRepository, times(1))
        .findStuckDeployments(any(OffsetDateTime.class));
    verify(deploymentRepository, times(1))
        .findStuckDeployments(any(OffsetDateTime.class));
    verify(heliosDeploymentRepository,
        times(2)).save(any(HeliosDeployment.class));
    verify(deploymentRepository,
        times(0)).save(any(Deployment.class));
    assertEquals(HeliosDeployment.Status.FAILED, stuckHeliosDeployment1.getStatus());
    assertEquals(HeliosDeployment.Status.FAILED, stuckHeliosDeployment2.getStatus());
    assertEquals(HeliosDeployment.Status.IN_PROGRESS, recentHeliosDeployment.getStatus());
    assertEquals(Deployment.State.IN_PROGRESS, stuckDeployment1.getState());
    assertEquals(Deployment.State.IN_PROGRESS, stuckDeployment2.getState());
    assertEquals(Deployment.State.IN_PROGRESS, recentDeployment.getState());
  }

  @Test
  void testMarkStuckDeploymentsAsFailureWhenNoStuckDeployments() {
    // Arrange
    when(heliosDeploymentRepository.findStuckDeployments(any(OffsetDateTime.class)))
        .thenReturn(Collections.emptyList());
    when(deploymentRepository.findStuckDeployments(any(OffsetDateTime.class)))
        .thenReturn(Collections.emptyList());

    // Act
    deploymentRecoveryService.markStuckDeploymentsAsFailure();

    // Assert
    verify(heliosDeploymentRepository, times(1))
        .findStuckDeployments(any(OffsetDateTime.class));
    verify(deploymentRepository, times(1))
        .findStuckDeployments(any(OffsetDateTime.class));
    verify(heliosDeploymentRepository, never()).save(any(HeliosDeployment.class));
    verify(deploymentRepository, never()).save(any(Deployment.class));
  }

}
