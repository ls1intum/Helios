package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsService;
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

  // Every 5 seconds
  @Scheduled(fixedRate = 5000)
  public void unlockExpiredEnvironments() {
    List<Environment> lockedEnvironments = environmentRepository.findByLockedTrue();
    for (Environment environment : lockedEnvironments) {
      try {
        // Determine the applicable thresholds
        Long lockExpirationThreshold =
            environment.getLockExpirationThreshold() != null
                ? environment.getLockExpirationThreshold()
                : gitRepoSettingsService
                    .getGitRepoSettingsByRepositoryId(environment.getRepository().getRepositoryId())
                    .map(GitRepoSettings::getLockExpirationThreshold)
                    .orElse(0L);

        OffsetDateTime lockedAt = environment.getLockedAt();
        if (lockedAt == null) {
          continue;
        }

        long lockedDuration = (OffsetDateTime.now().toEpochSecond() - lockedAt.toEpochSecond());
        if (lockedDuration >= lockExpirationThreshold) {
          environment.setLocked(false);
          environment.setLockedBy(null);
          environment.setLockedAt(null);

          Optional<EnvironmentLockHistory> openLock =
              lockHistoryRepository.findCurrentLockForEnabledEnvironment(environment.getId());
          if (openLock.isPresent()) {
            EnvironmentLockHistory openLockHistory = openLock.get();
            openLockHistory.setUnlockedAt(OffsetDateTime.now());
            lockHistoryRepository.save(openLockHistory);
          }

          environmentRepository.save(environment);
        }
      } catch (Exception e) {
        // Log error
      }
    }
  }
}
