package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsDto;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsService;
import de.tum.cit.aet.helios.nats.NatsNotificationPublisherService;
import de.tum.cit.aet.helios.notification.email.LockExpiredPayload;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserConverter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class EnvironmentScheduler {
  private final EnvironmentRepository environmentRepository;
  private final GitRepoSettingsService gitRepoSettingsService;
  private final EnvironmentLockHistoryRepository lockHistoryRepository;
  private final GitHubUserConverter userConverter;
  private final UserRepository userRepository;
  private final org.springframework.core.env.Environment springEnvironment;
  private final NatsNotificationPublisherService notificationPublisherService;

  // Every minute
  @Scheduled(fixedRate = 60000)
  public void unlockExpiredEnvironments() {
    if (springEnvironment.matchesProfiles("openapi")) {
      log.info("OpenAPI profile detected. Skipping Status Check Scheduler.");
      return;
    }

    Integer numberOfAutoUnlockedEnvironments = 0;
    List<Environment> lockedEnvironments = environmentRepository.findByLockedTrue();
    for (Environment environment : lockedEnvironments) {
      try {
        // TODO use directly the lockWillExpireAt field instead of calculating it as below
        // Determine the applicable thresholds in minutes
        Long lockExpirationThresholdMinutes =
            environment.getLockExpirationThreshold() != null
                ? environment.getLockExpirationThreshold()
                : gitRepoSettingsService
                    .getOrCreateGitRepoSettingsByRepositoryId(
                        environment.getRepository().getRepositoryId())
                    .map(GitRepoSettingsDto::lockExpirationThreshold)
                    .orElse(-1L);

        // Skip if lock expiration threshold is set to -1
        if (lockExpirationThresholdMinutes == -1) {
          continue;
        }

        // Convert minutes to seconds for comparison
        long lockExpirationThresholdSeconds = lockExpirationThresholdMinutes * 60;

        OffsetDateTime lockedAt = environment.getLockedAt();
        if (lockedAt == null) {
          continue;
        }

        long lockedDurationSeconds =
            OffsetDateTime.now().toEpochSecond() - lockedAt.toEpochSecond();
        if (lockedDurationSeconds >= lockExpirationThresholdSeconds) {

          // Publish email notification for lock expiration
          notificationPublisherService.send(
              environment.getLockedBy(),
              new LockExpiredPayload(
                  environment.getLockedBy().getLogin(),
                  environment.getName(),
                  environment.getRepository().getRepositoryId().toString(),
                  environment.getRepository().getNameWithOwner()
              ));

          environment.setLocked(false);
          environment.setLockedBy(null);
          environment.setLockedAt(null);
          environment.setLockWillExpireAt(null);
          environment.setLockReservationExpiresAt(null);

          Optional<EnvironmentLockHistory> openLock =
              lockHistoryRepository.findCurrentLockForEnabledEnvironment(environment.getId());
          if (openLock.isPresent()) {
            EnvironmentLockHistory openLockHistory = openLock.get();
            openLockHistory.setUnlockedAt(OffsetDateTime.now());
            openLockHistory.setUnlockedBy(
                userRepository
                    .findById(Long.parseLong("-2"))
                    .orElseGet(
                        () -> userRepository.save(userConverter.convertToExpirationPicker())));
            lockHistoryRepository.save(openLockHistory);
            numberOfAutoUnlockedEnvironments++;
            log.info(
                "Auto-unlocked environment {} after {} minutes",
                environment.getId(),
                lockExpirationThresholdMinutes);
          }

          environmentRepository.save(environment);
        }
      } catch (Exception e) {
        log.error("Error unlocking environment {}: {}", environment.getId(), e.getMessage());
      }
    }
    if (numberOfAutoUnlockedEnvironments > 0) {
      log.info("Auto-unlocked {} environments", numberOfAutoUnlockedEnvironments);
    }
  }
}
