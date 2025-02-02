package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import java.util.List;
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
  private final org.springframework.core.env.Environment springEnvironment;

  /*
   * Runs status checks for all environments with a status check type configured
   * at a fixed interval.
   *
   * The interval is configurable via the status-check.interval property.
   * Defaults to 120 seconds.
   */
  @Scheduled(fixedRateString = "${status-check.interval:120s}")
  public void runScheduledChecks() {
    if (springEnvironment.matchesProfiles("openapi")) {
      log.info("OpenAPI profile detected. Skipping Status Check Scheduler.");
      return;
    }
    log.info("Starting scheduled status checks.");

    List<Environment> environments = environmentRepository.findByStatusCheckTypeIsNotNull();

    log.info("Found {} environments with status check type configured.", environments.size());

    List<CompletableFuture<Void>> futures =
        environments.stream().map(env -> statusCheckService.performStatusCheck(env)).toList();

    // Wait for all status checks to complete
    futures.forEach(CompletableFuture::join);

    log.info("Scheduled status checks completed.");
  }
}
