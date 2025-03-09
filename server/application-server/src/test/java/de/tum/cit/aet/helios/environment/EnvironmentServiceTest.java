package de.tum.cit.aet.helios.environment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsService;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
public class EnvironmentServiceTest {

  @Mock private AuthService authService;
  @Mock private EnvironmentRepository environmentRepository;
  @Mock private EnvironmentLockHistoryRepository lockHistoryRepository;
  @Mock private HeliosDeploymentRepository heliosDeploymentRepository;
  @Mock private ReleaseCandidateRepository releaseCandidateRepository;
  @Mock private DeploymentRepository deploymentRepository;
  @Mock private GitRepoSettingsService gitRepoSettingsService;
  @Mock private EnvironmentScheduler environmentScheduler;
  @Mock private WorkflowRepository workflowRepository;

  @InjectMocks private EnvironmentService environmentService;

  private Environment environment;
  private User user;
  private GitRepository gitRepository;

  @BeforeEach
  public void setUp() {
    gitRepository = new GitRepository();
    gitRepository.setRepositoryId(1L);

    environment = new Environment();
    environment.setId(1L);
    environment.setRepository(gitRepository);
    environment.setEnabled(true);
    environment.setType(Environment.Type.TEST);

    user = new User();
    user.setId(1L);
  }

  @Test
  public void testGetEnvironmentById() {
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Optional<EnvironmentDto> result = environmentService.getEnvironmentById(1L);

    assertTrue(result.isPresent());
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testGetEnvironmentTypeById() {
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Optional<Environment.Type> result = environmentService.getEnvironmentTypeById(1L);

    assertTrue(result.isPresent());
    assertEquals(Environment.Type.TEST, result.get());
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testGetAllEnvironments() {
    when(environmentRepository.findAllByOrderByNameAsc())
        .thenReturn(List.of(environment, environment));

    List<EnvironmentDto> result = environmentService.getAllEnvironments();

    EnvironmentDto dto = EnvironmentDto.fromEnvironment(environment);

    assertEquals(2, result.size());
    assertEquals(List.of(dto, dto), result);
    verify(environmentRepository, times(1)).findAllByOrderByNameAsc();
  }

  @Test
  public void testGetAllEnabledEnvironments() {
    when(environmentRepository.findByEnabledTrueOrderByNameAsc())
        .thenReturn(List.of(environment, environment));

    List<EnvironmentDto> result = environmentService.getAllEnabledEnvironments();

    EnvironmentDto dto = EnvironmentDto.fromEnvironment(environment);

    assertEquals(2, result.size());
    assertEquals(List.of(dto, dto), result);
    verify(environmentRepository, times(1)).findByEnabledTrueOrderByNameAsc();
  }

  @Test
  public void testLockEnvironment() {
    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));
    when(lockHistoryRepository.saveAndFlush(any(EnvironmentLockHistory.class)))
        .thenReturn(new EnvironmentLockHistory());
    when(environmentRepository.save(any(Environment.class))).thenReturn(environment);

    Optional<Environment> result = environmentService.lockEnvironment(1L);

    assertTrue(result.isPresent());
    assertTrue(result.get().isLocked());
    verify(environmentRepository, times(1)).findById(1L);
    verify(environmentRepository, times(1)).save(any(Environment.class));
  }

  @Test
  public void testLockEnvironmentAlreadyLocked() {
    environment.setLocked(true);
    environment.setLockedAt(OffsetDateTime.now());
    environment.setLockedBy(user);

    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Optional<Environment> result = environmentService.lockEnvironment(1L);

    assertTrue(result.isPresent());
    assertTrue(result.get().isLocked());
    // Should not modify lockedAt
    assertTrue(result.get().getLockedAt().isEqual(environment.getLockedAt()));
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testLockEnvironmentAlreadyLockedByOtherUser() {
    final User otherUser = new User();
    otherUser.setId(2L);
    environment.setLocked(true);
    environment.setLockedBy(otherUser);

    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Exception exception =
        assertThrows(
            EnvironmentException.class,
            () -> {
              environmentService.lockEnvironment(1L);
            });

    assertTrue(exception.getMessage().contains("Environment is locked by another user"));
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testExtendEnvironmentLock() {
    final OffsetDateTime dateTimeNow = OffsetDateTime.now();
    final Long lockExpirationThreshold = 20L;
    final Long lockReservationExpirationThreshold = 10L;
    environment.setLocked(true);
    environment.setLockedBy(user);
    environment.setLockedAt(OffsetDateTime.now().minusMinutes(5L));
    environment.setLockExpirationThreshold(lockExpirationThreshold);
    environment.setLockReservationThreshold(lockReservationExpirationThreshold);

    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));
    when(environmentRepository.save(any(Environment.class))).thenReturn(environment);

    Optional<Environment> result = environmentService.extendEnvironmentLock(1L);

    assertTrue(result.isPresent());
    assertTrue(
        result
            .get()
            .getLockWillExpireAt()
            .isAfter(dateTimeNow.plusMinutes(lockExpirationThreshold)));
    assertTrue(
        result
            .get()
            .getLockReservationExpiresAt()
            .isAfter(dateTimeNow.plusMinutes(lockReservationExpirationThreshold)));
    verify(environmentRepository, times(1)).findById(1L);
    verify(environmentRepository, times(1)).save(any(Environment.class));
  }

  @Test
  public void testExtendEnvironmentLockNotLockedFails() {
    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Exception exception =
        assertThrows(
            EnvironmentException.class,
            () -> {
              environmentService.extendEnvironmentLock(1L);
            });

    assertEquals("Environment is not locked. Cannot extend lock.", exception.getMessage());
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testExtendProductionEnvironmentLockFails() {
    environment.setType(Environment.Type.PRODUCTION);

    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Exception exception =
        assertThrows(
            EnvironmentException.class,
            () -> {
              environmentService.extendEnvironmentLock(1L);
            });

    assertEquals("Only TEST environments can have their locks extended", exception.getMessage());
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testExtendDisabledEnvironmentLockFails() {
    environment.setEnabled(false);

    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Exception exception =
        assertThrows(
            EnvironmentException.class,
            () -> {
              environmentService.extendEnvironmentLock(1L);
            });

    assertEquals("Environment is disabled", exception.getMessage());
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testUnlockEnvironment() {
    environment.setLocked(true);
    environment.setLockedBy(user);
    environment.setLockedAt(OffsetDateTime.now());

    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));
    when(environmentRepository.save(any(Environment.class))).thenReturn(environment);

    EnvironmentDto result = environmentService.unlockEnvironment(1L);

    assertNotNull(result);
    assertFalse(result.locked());
    assertNull(result.lockedBy());
    assertNull(result.updatedAt());
    assertNull(result.lockedAt());
    assertNull(result.lockWillExpireAt());
    assertNull(result.lockReservationWillExpireAt());
    verify(environmentRepository, times(1)).findById(1L);
    verify(environmentRepository, times(1)).save(any(Environment.class));
  }

  @Test
  public void testUnlockEnvironmentNotLocked() {
    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Exception exception =
        assertThrows(
            EnvironmentException.class,
            () -> {
              environmentService.unlockEnvironment(1L);
            });

    assertEquals("Environment is not locked", exception.getMessage());
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testMaintainerCanUnlockOtherUsersLock() {
    final User otherUser = new User();
    otherUser.setId(2L);
    environment.setLocked(true);
    environment.setLockedBy(otherUser);

    when(authService.isAtLeastMaintainer()).thenReturn(true);
    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    EnvironmentDto result = environmentService.unlockEnvironment(1L);

    assertNotNull(result);
    assertFalse(result.locked());
    assertNull(result.lockedBy());
    assertNull(result.updatedAt());
    assertNull(result.lockedAt());
    assertNull(result.lockWillExpireAt());
    assertNull(result.lockReservationWillExpireAt());
    verify(environmentRepository, times(1)).findById(1L);
    verify(environmentRepository, times(1)).save(any(Environment.class));
  }

  @Test
  public void testUserCanNotUnlockOtherUsersLock() {
    final User otherUser = new User();
    otherUser.setId(2L);
    environment.setLocked(true);
    environment.setLockedBy(otherUser);

    when(authService.isAtLeastMaintainer()).thenReturn(false);
    when(authService.getUserFromGithubId()).thenReturn(user);
    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    Exception exception =
        assertThrows(
            SecurityException.class,
            () -> {
              environmentService.unlockEnvironment(1L);
            });

    assertTrue(
        exception.getMessage().contains("You do not have permission to unlock this environment"));
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testUpdateLockExpirationAndReservation() {
    Environment lockedEnvironment = new Environment();
    lockedEnvironment.setId(1L);
    lockedEnvironment.setLocked(true);
    lockedEnvironment.setRepository(gitRepository);

    when(environmentRepository.findByRepositoryRepositoryIdAndLockedTrue(1L))
        .thenReturn(List.of(lockedEnvironment));
    when(environmentRepository.save(any(Environment.class))).thenReturn(lockedEnvironment);

    environmentService.updateLockExpirationAndReservation(1L);

    verify(environmentRepository, times(1)).findByRepositoryRepositoryIdAndLockedTrue(1L);
    verify(environmentRepository, times(1)).save(lockedEnvironment);
  }

  @Test
  public void testSetLockedEnvironmentAsDisabled() {
    environment.setLocked(true);

    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));

    environment.setEnabled(false);

    Exception exception =
        assertThrows(
            EnvironmentException.class,
            () -> {
              environmentService.updateEnvironment(1L, EnvironmentDto.fromEnvironment(environment));
            });

    assertTrue(exception.getMessage().contains("Environment is locked and can not be disabled"));
    verify(environmentRepository, times(1)).findById(1L);
  }

  @Test
  public void testUpdateEnvironment() {
    final Workflow wf = new Workflow();
    wf.setId(1L);
    wf.setRepository(gitRepository);
    final Long threshold = 10L;

    when(environmentRepository.findById(1L)).thenReturn(Optional.of(environment));
    when(workflowRepository.findById(1L)).thenReturn(Optional.of(wf));

    environment.setEnabled(false);
    environment.setType(Environment.Type.PRODUCTION);
    environment.setUpdatedAt(OffsetDateTime.now());
    environment.setInstalledApps(List.of("app"));
    environment.setDescription("blah");
    environment.setServerUrl("serverUrl");
    environment.setDeploymentWorkflow(wf);
    environment.setLockExpirationThreshold(threshold);
    environment.setLockReservationThreshold(threshold);

    Optional<EnvironmentDto> environmentDto =
        environmentService.updateEnvironment(1L, EnvironmentDto.fromEnvironment(environment));

    assertTrue(environmentDto.isPresent());
    assertEquals("blah", environmentDto.get().description());
    assertEquals("serverUrl", environmentDto.get().serverUrl());
    assertEquals(1, environmentDto.get().installedApps().size());
    assertEquals("app", environmentDto.get().installedApps().get(0));
    assertEquals(Environment.Type.PRODUCTION, environmentDto.get().type());
    assertEquals(wf.getId(), environmentDto.get().deploymentWorkflow().id());
    assertEquals(threshold, environmentDto.get().lockExpirationThreshold());
    assertEquals(threshold, environmentDto.get().lockReservationThreshold());
    assertFalse(environmentDto.get().enabled());

    verify(environmentRepository, times(1)).findById(1L);
    verify(workflowRepository, times(1)).findById(1L);
  }
}
