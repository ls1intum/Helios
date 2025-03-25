package de.tum.cit.aet.helios.deployment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.branch.BranchService;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistory;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistoryRepository;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowService;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

@RunWith(MockitoJUnitRunner.class)
@ExtendWith(MockitoExtension.class)
public class DeploymentServiceTest {

  @InjectMocks private DeploymentService deploymentService;
  @Mock private DeploymentRepository deploymentRepository;
  @Mock private GitHubService gitHubService;
  @Mock private EnvironmentService environmentService;
  @Mock private WorkflowService workflowService;
  @Mock private AuthService authService;
  @Mock private HeliosDeploymentRepository heliosDeploymentRepository;
  @Mock private EnvironmentLockHistoryRepository lockHistoryRepository;
  @Mock private EnvironmentRepository environmentRepository;
  @Mock private BranchService branchService;
  @Mock private PullRequestRepository pullRequestRepository;

  private Deployment deployment;
  private GitRepository gitRepository;
  private Environment environment;
  private HeliosDeployment heliosDeployment;

  @BeforeEach
  public void setUp() {
    heliosDeployment = new HeliosDeployment();
    heliosDeployment.setId(1L);
    heliosDeployment.setEnvironment(environment);

    gitRepository = new GitRepository();
    gitRepository.setRepositoryId(1L);

    environment = new Environment();
    environment.setId(1L);
    environment.setRepository(gitRepository);

    deployment = new Deployment();
    deployment.setId(1L);
    deployment.setRepository(gitRepository);
    deployment.setEnvironment(environment);
  }

  @Test
  public void testGetDeploymentById() {
    when(deploymentRepository.findById(1L)).thenReturn(Optional.of(deployment));

    Optional<DeploymentDto> result = deploymentService.getDeploymentById(1L);

    DeploymentDto deploymentDto = DeploymentDto.fromDeployment(deployment);

    assertTrue(result.isPresent());
    assertEquals(deploymentDto, result.get());

    verify(deploymentRepository, times(1)).findById(1L);
  }

  @Test
  public void testGetAllDeployments() {

    when(deploymentRepository.findAll()).thenReturn(List.of(deployment, deployment));

    List<DeploymentDto> result = deploymentService.getAllDeployments();

    DeploymentDto deploymentDto = DeploymentDto.fromDeployment(deployment);

    assertEquals(2, result.size());
    Assertions.assertIterableEquals(List.of(deploymentDto, deploymentDto), result);
  }

  @Test
  public void testGetDeploymentsByEnvironmentId() {
    when(deploymentRepository.findByEnvironmentIdOrderByCreatedAtDesc(1L))
        .thenReturn(List.of(deployment));

    List<DeploymentDto> result = deploymentService.getDeploymentsByEnvironmentId(1L);

    DeploymentDto deploymentDto = DeploymentDto.fromDeployment(deployment);

    assertEquals(1, result.size());
    Assertions.assertIterableEquals(List.of(deploymentDto), result);
  }

  @Test
  public void testGetLatestDeploymentByEnvironmentId() {
    when(deploymentRepository.findFirstByEnvironmentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(deployment));

    Optional<DeploymentDto> result = deploymentService.getLatestDeploymentByEnvironmentId(1L);

    DeploymentDto deploymentDto = DeploymentDto.fromDeployment(deployment);

    assertTrue(result.isPresent());
    assertEquals(deploymentDto, result.get());
  }

  @Test
  public void testDeployWithInvalidDeployRequest() {
    DeployRequest deployRequest = mock(DeployRequest.class);
    when(deployRequest.environmentId()).thenReturn(null);
    when(deployRequest.branchName()).thenReturn("main");

    Exception exception =
        assertThrows(
            DeploymentException.class,
            () -> {
              deploymentService.deployToEnvironment(deployRequest);
            });

    assertTrue(exception.getMessage().contains("Environment ID and branch name must not be null"));

    when(deployRequest.environmentId()).thenReturn(1L);
    when(deployRequest.branchName()).thenReturn(null);

    Exception exception2 =
        assertThrows(
            DeploymentException.class,
            () -> {
              deploymentService.deployToEnvironment(deployRequest);
            });

    assertTrue(exception2.getMessage().contains("Environment ID and branch name must not be null"));
  }

  @Test
  public void testDeployToEnvironmentWithoutPermissions() {
    final DeployRequest deployRequest = new DeployRequest(1L, "main", "sha");

    when(authService.hasRole(anyString())).thenReturn(false);
    when(environmentService.getEnvironmentTypeById(1L))
        .thenReturn(Optional.of(Environment.Type.PRODUCTION));

    Exception exception =
        assertThrows(
            SecurityException.class,
            () -> {
              deploymentService.deployToEnvironment(deployRequest);
            });

    assertTrue(
        exception.getMessage().contains("Insufficient permissions to deploy to this environment"));
  }

  @Test
  public void testDeployToEnvironmentWithoutWorkflow() {
    final DeployRequest deployRequest = new DeployRequest(1L, "main", "sha");

    when(environmentService.getEnvironmentTypeById(1L))
        .thenReturn(Optional.of(Environment.Type.PRODUCTION));
    when(authService.hasRole(anyString())).thenReturn(true);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Exception exception =
        assertThrows(
            NoSuchElementException.class,
            () -> {
              deploymentService.deployToEnvironment(deployRequest);
            });

    assertTrue(exception.getMessage().contains("No deployment workflow found for environment"));
  }

  @Test
  public void testDeployToEnvironment() {
    final DeployRequest deployRequest = new DeployRequest(1L, "main", "sha");

    Workflow wf = new Workflow();
    wf.setId(1L);

    environment.setDeploymentWorkflow(wf);

    when(environmentService.getEnvironmentTypeById(1L))
        .thenReturn(Optional.of(Environment.Type.PRODUCTION));
    when(authService.hasRole(anyString())).thenReturn(true);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));
    when(heliosDeploymentRepository.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));

    deploymentService.deployToEnvironment(deployRequest);
  }

  @Test
  public void testCanDeployWithRoleAdmin() {
    when(authService.hasRole(anyString())).thenReturn(false);
    when(authService.hasRole("ROLE_ADMIN")).thenReturn(true);

    for (Environment.Type type : Environment.Type.values()) {
      assertTrue(deploymentService.canDeployToEnvironment(type));
    }
  }

  @Test
  public void testCanDeployWithRoleWrite() {
    when(authService.hasRole(anyString())).thenReturn(false);
    when(authService.hasRole("ROLE_WRITE")).thenReturn(true);

    assertFalse(deploymentService.canDeployToEnvironment(Environment.Type.PRODUCTION));
    assertFalse(deploymentService.canDeployToEnvironment(Environment.Type.STAGING));
    assertTrue(deploymentService.canDeployToEnvironment(Environment.Type.TEST));
  }

  @Test
  public void testCanDeployWithRoleMaintainer() {
    when(authService.hasRole(anyString())).thenReturn(false);
    when(authService.hasRole("ROLE_MAINTAINER")).thenReturn(true);

    assertFalse(deploymentService.canDeployToEnvironment(Environment.Type.PRODUCTION));
    assertFalse(deploymentService.canDeployToEnvironment(Environment.Type.STAGING));
    assertTrue(deploymentService.canDeployToEnvironment(Environment.Type.TEST));
  }

  @Test
  public void testCanDeployWithNoRole() {
    when(authService.hasRole(anyString())).thenReturn(false);

    for (Environment.Type type : Environment.Type.values()) {
      assertFalse(deploymentService.canDeployToEnvironment(type));
    }
  }

  @Test
  public void testGetActivityHistoryByEnvironmentId() {
    final OffsetDateTime now = OffsetDateTime.now();

    EnvironmentLockHistory lockHistory = new EnvironmentLockHistory();
    lockHistory.setId(1L);

    // Set relevant dates to check correct order later on
    heliosDeployment.setCreatedAt(now.minusMinutes(1));
    heliosDeployment.setEnvironment(environment);
    deployment.setCreatedAt(now.minusMinutes(2));
    lockHistory.setLockedAt(now.minusMinutes(3));
    lockHistory.setUnlockedAt(now);

    when(deploymentRepository.findByEnvironmentIdOrderByCreatedAtDesc(1L))
        .thenReturn(List.of(deployment));
    when(lockHistoryRepository.findLockHistoriesByEnvironment(1L)).thenReturn(List.of(lockHistory));
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));
    when(heliosDeploymentRepository.findByEnvironmentAndDeploymentIdIsNull(environment))
        .thenReturn(List.of(heliosDeployment));

    List<ActivityHistoryDto> result = deploymentService.getActivityHistoryByEnvironmentId(1L);

    ActivityHistoryDto heliosDto = ActivityHistoryDto.fromHeliosDeployment(heliosDeployment);
    ActivityHistoryDto lockDto =
        ActivityHistoryDto.fromEnvironmentLockHistory("LOCK_EVENT", lockHistory);
    ActivityHistoryDto unlockDto =
        ActivityHistoryDto.fromEnvironmentLockHistory("UNLOCK_EVENT", lockHistory);
    ActivityHistoryDto deploymentDto = ActivityHistoryDto.fromDeployment(deployment);

    assertEquals(4, result.size());
    Assertions.assertIterableEquals(List.of(unlockDto, heliosDto, deploymentDto, lockDto), result);
  }

  @Test
  public void testCanRedeployWithNoLatestDeployment()
      throws NoSuchMethodException,
          IllegalAccessException,
          java.lang.reflect.InvocationTargetException {
    final Method canRedeployMethod =
        DeploymentService.class.getDeclaredMethod("canRedeploy", Environment.class, long.class);
    canRedeployMethod.setAccessible(true);

    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.empty());

    assertTrue((boolean) canRedeployMethod.invoke(deploymentService, environment, 10L));
  }

  @Test
  public void testCanRedeployWithPreviousFailedDeployment()
      throws NoSuchMethodException,
          IllegalAccessException,
          java.lang.reflect.InvocationTargetException {
    final Method canRedeployMethod =
        DeploymentService.class.getDeclaredMethod("canRedeploy", Environment.class, long.class);
    canRedeployMethod.setAccessible(true);

    heliosDeployment.setStatus(HeliosDeployment.Status.FAILED);
    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.of(heliosDeployment));
    assertTrue((boolean) canRedeployMethod.invoke(deploymentService, environment, 10L));

    heliosDeployment.setStatus(HeliosDeployment.Status.IO_ERROR);
    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.of(heliosDeployment));
    assertTrue((boolean) canRedeployMethod.invoke(deploymentService, environment, 10L));

    heliosDeployment.setStatus(HeliosDeployment.Status.UNKNOWN);
    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.of(heliosDeployment));
    assertTrue((boolean) canRedeployMethod.invoke(deploymentService, environment, 10L));
  }

  @Test
  public void testCanNotRedeployWithPreviousDeploymentAndExpiredTimeout()
      throws NoSuchMethodException,
          IllegalAccessException,
          java.lang.reflect.InvocationTargetException {
    final Method canRedeployMethod =
        DeploymentService.class.getDeclaredMethod("canRedeploy", Environment.class, long.class);
    canRedeployMethod.setAccessible(true);
    heliosDeployment.setStatusUpdatedAt(OffsetDateTime.now().minusMinutes(1));

    heliosDeployment.setStatus(HeliosDeployment.Status.IN_PROGRESS);
    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.of(heliosDeployment));
    assertTrue((boolean) canRedeployMethod.invoke(deploymentService, environment, 0L));
    assertEquals(HeliosDeployment.Status.UNKNOWN, heliosDeployment.getStatus());

    heliosDeployment.setStatus(HeliosDeployment.Status.WAITING);
    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.of(heliosDeployment));
    assertTrue((boolean) canRedeployMethod.invoke(deploymentService, environment, 0L));
    assertEquals(HeliosDeployment.Status.UNKNOWN, heliosDeployment.getStatus());

    heliosDeployment.setStatus(HeliosDeployment.Status.QUEUED);
    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.of(heliosDeployment));
    assertTrue((boolean) canRedeployMethod.invoke(deploymentService, environment, 0L));
    assertEquals(HeliosDeployment.Status.UNKNOWN, heliosDeployment.getStatus());

    verify(heliosDeploymentRepository, times(3)).save(heliosDeployment);
  }

  @Test
  public void testCanNotRedeployWithPreviousDeploymentAndNotExpiredTimeout()
      throws NoSuchMethodException,
          IllegalAccessException,
          java.lang.reflect.InvocationTargetException {
    final Method canRedeployMethod =
        DeploymentService.class.getDeclaredMethod("canRedeploy", Environment.class, long.class);
    canRedeployMethod.setAccessible(true);
    heliosDeployment.setStatusUpdatedAt(OffsetDateTime.now().minusMinutes(1));

    heliosDeployment.setStatus(HeliosDeployment.Status.IN_PROGRESS);
    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.of(heliosDeployment));
    assertFalse((boolean) canRedeployMethod.invoke(deploymentService, environment, 10L));

    heliosDeployment.setStatus(HeliosDeployment.Status.WAITING);
    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.of(heliosDeployment));
    assertFalse((boolean) canRedeployMethod.invoke(deploymentService, environment, 10L));

    heliosDeployment.setStatus(HeliosDeployment.Status.QUEUED);
    when(heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment))
        .thenReturn(Optional.of(heliosDeployment));
    assertFalse((boolean) canRedeployMethod.invoke(deploymentService, environment, 10L));
  }
}
