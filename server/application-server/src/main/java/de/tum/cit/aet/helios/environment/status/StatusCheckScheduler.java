package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class StatusCheckScheduler {
  private final EnvironmentRepository environmentRepository;
  private final StatusCheckService statusCheckService;
  private final EnvironmentStatusConfig config;
  private final org.springframework.core.env.Environment springEnvironment;

  /*
   * Runs status checks for all environments with a status check type configured
   * at a fixed interval.
   *
   * The interval is configurable via the status-check.interval property.
   * Defaults to 120 seconds.
   */
  @Scheduled(fixedRateString = "${status-check.recent-interval:10s}")
  public void runScheduledChecks() {
    if (springEnvironment.matchesProfiles("openapi")) {
      log.info("OpenAPI profile detected. Skipping Status Check Scheduler.");
      return;
    }

    final Instant now = Instant.now();
    final long start = System.currentTimeMillis() / 1000;

    log.debug("Starting scheduled status checks.");

    // 1. Let's get all environments with a status check type configured
    // and pre-load the latest status for each environment (if it exists)
    List<Environment> environments = environmentRepository.findByStatusCheckTypeIsNotNullWithLatestStatus();

    // 2. Now let's determine if the environments are stable, meaning that we would
    // check them less frequently.
    var stableEnvironments = environments.stream()
        .filter(env -> {
          if (env.getStatusChangedAt() == null) {
            return true;
          }

          // Stable means that the environment has been in the same state for a while
          // (threshold)
          return Duration.between(env.getStatusChangedAt(), now).toSeconds() >= this.config.getCheckRecentThreshold()
              .toSeconds();
        })
        .toList();

    // 3. Filter the stable environments, so we only include the ones that we should
    // check now
    // (the last status check is older than the stable interval)
    var stableEnvironmentsToCheck = stableEnvironments.stream()
        .filter(env -> {
          Optional<EnvironmentStatus> latestStatus = env.getLatestStatus();

          if (latestStatus.isEmpty()) {
            return false;
          }

          return Duration.between(latestStatus.get().getCheckTimestamp(), now).toSeconds() >= this.config
              .getCheckStableInterval()
              .toSeconds();
        })
        .toList();

    // 4. Set all non-stable environments as recent environments
    var recentEnvironments = environments.stream()
        .filter(env -> !stableEnvironments.contains(env))
        .toList();

    // 5. Combine the stable environments to check and the recent environments
    var environmentsToCheck = List.of(stableEnvironmentsToCheck, recentEnvironments).stream()
        .flatMap(List::stream)
        .toList();

    log.debug("Found {} environments with status check type configured. {} stable (checking {} now), {} recent",
        environments.size(),
        stableEnvironments.size(),
        stableEnvironmentsToCheck.size(),
        recentEnvironments.size());

    List<CompletableFuture<Void>> futures = environmentsToCheck.stream()
        .map(env -> statusCheckService.performStatusCheck(env))
        .toList();

    // Wait for all status checks to complete
    futures.forEach(CompletableFuture::join);

    final long end = System.currentTimeMillis() / 1000;
    final double duration = Math.round((end - start) * 10) / 10.0;

    final long intervalSeconds = this.config.getCheckInterval().getSeconds();

    if (intervalSeconds > duration) {
      log.warn("Scheduled status checks took longer than the configured interval of {} seconds.",
          intervalSeconds);
    } else {
      log.debug("Scheduled status checks completed in {} seconds.", duration);
    }
  }
}
