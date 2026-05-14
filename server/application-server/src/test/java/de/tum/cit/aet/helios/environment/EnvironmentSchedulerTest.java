package de.tum.cit.aet.helios.environment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.environment.ws.EnvironmentDeploymentWebSocketPublisher;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsService;
import de.tum.cit.aet.helios.nats.NatsNotificationPublisherService;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserConverter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnvironmentSchedulerTest {

  @Mock private EnvironmentRepository environmentRepository;
  @Mock private GitRepoSettingsService gitRepoSettingsService;
  @Mock private EnvironmentLockHistoryRepository lockHistoryRepository;
  @Mock private GitHubUserConverter userConverter;
  @Mock private UserRepository userRepository;
  @Mock private org.springframework.core.env.Environment springEnvironment;
  @Mock private NatsNotificationPublisherService notificationPublisherService;
  @Mock private EnvironmentDeploymentWebSocketPublisher environmentDeploymentWebSocketPublisher;

  @InjectMocks private EnvironmentScheduler environmentScheduler;

  private Environment environment;
  private EnvironmentLockHistory lockHistory;
  private User lockedBy;

  @BeforeEach
  void setUp() {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(42L);
    repository.setNameWithOwner("owner/repo");

    lockedBy = new User();
    lockedBy.setId(1L);
    lockedBy.setLogin("locked-user");

    environment = new Environment();
    environment.setId(7L);
    environment.setName("test-env");
    environment.setRepository(repository);
    environment.setLocked(true);
    environment.setLockedBy(lockedBy);
    environment.setLockedAt(OffsetDateTime.now().minusMinutes(10));
    environment.setLockExpirationThreshold(5L);

    lockHistory = new EnvironmentLockHistory();
    lockHistory.setEnvironment(environment);
    lockHistory.setLockedBy(lockedBy);
    lockHistory.setLockedAt(environment.getLockedAt());
  }

  @Test
  void unlockExpiredEnvironmentsPublishesInvalidationAfterAutoUnlock() {
    User expirationUser = new User();
    expirationUser.setId(-2L);

    when(springEnvironment.matchesProfiles("openapi")).thenReturn(false);
    when(environmentRepository.findByLockedTrue()).thenReturn(List.of(environment));
    when(lockHistoryRepository.findCurrentLockForEnabledEnvironment(7L))
        .thenReturn(Optional.of(lockHistory));
    when(userRepository.findById(-2L)).thenReturn(Optional.of(expirationUser));

    environmentScheduler.unlockExpiredEnvironments();

    verify(environmentRepository).save(environment);
    verify(environmentDeploymentWebSocketPublisher).publishAfterCommit(environment);
  }

  @Test
  void unlockExpiredEnvironmentsDoesNotPublishForNonExpiredLock() {
    environment.setLockedAt(OffsetDateTime.now());

    when(springEnvironment.matchesProfiles("openapi")).thenReturn(false);
    when(environmentRepository.findByLockedTrue()).thenReturn(List.of(environment));

    environmentScheduler.unlockExpiredEnvironments();

    verify(environmentRepository, never()).save(any(Environment.class));
    verify(environmentDeploymentWebSocketPublisher, never())
        .publishAfterCommit(any(Environment.class));
  }

  @Test
  void unlockExpiredEnvironmentsDoesNotPublishWhenOpenApiProfileIsActive() {
    when(springEnvironment.matchesProfiles("openapi")).thenReturn(true);

    environmentScheduler.unlockExpiredEnvironments();

    verify(environmentRepository, never()).findByLockedTrue();
    verify(environmentDeploymentWebSocketPublisher, never())
        .publishAfterCommit(any(Environment.class));
  }
}
