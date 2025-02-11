package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.environment.Environment;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
@Transactional
public class StatusCheckService {
  private final Map<StatusCheckType, StatusCheckStrategy> checkStrategies;
  private final EnvironmentStatusRepository statusRepository;
  private final EnvironmentStatusConfig config;

  /**
   * The number of status entries to keep for each environment
   * before deleting the oldest entries.
   */
  @Value("${status-check.keep-count:10}")
  private int keepCount;

  /**
   * Performs a status check on the given environment asynchronously as the status
   * check may take a while to complete.
   * 
   * <p>
   * The type of status check to be performed is determined by the environment's
   * configuration.
   * Saves the result of the status check after completion.
   *
   * @param environment the environment on which to perform the status check
   */
  @Async("statusCheckTaskExecutor")
  public CompletableFuture<Void> performStatusCheck(Environment environment) {
    return CompletableFuture.runAsync(() -> {
      final StatusCheckType checkType = environment.getStatusCheckType();

      log.debug("Starting status check for environment {} (ID: {}) with type {}",
          environment.getName(), environment.getId(), checkType);

      if (checkType == null) {
        log.warn("Skipping environment {} - no check type configured", environment.getId());
        return;
      }

      final StatusCheckStrategy strategy = checkStrategies.get(checkType);

      if (strategy == null) {
        log.error("No strategy found for check type {} in environment {}",
            checkType, environment.getId());
        return;
      }

      final StatusCheckResult result = strategy.check(environment);
      log.debug("Check completed for environment {} - success: {}, code: {}",
          environment.getId(), result.success(), result.httpStatusCode());

      saveStatusResult(environment, result);

      // Add a timeout to the check so no matter what check we use
      // we make sure that we don't wait for too long
    }).orTimeout(this.config.getCheckInterval().getSeconds(), TimeUnit.SECONDS).exceptionally(ex -> {
      handleTimeout(environment);
      return null;
    });
  }

  private void handleTimeout(Environment env) {
    StatusCheckResult result = new StatusCheckResult(false, 0, Map.of("timeout", true));
    log.error("Status check timed out for environment {}", env.getId());
    saveStatusResult(env, result);
  }

  private void saveStatusResult(Environment environment, StatusCheckResult result) {
    EnvironmentStatus status = new EnvironmentStatus();

    status.setEnvironment(environment);
    status.setCheckType(environment.getStatusCheckType());
    status.setSuccess(result.success());
    status.setHttpStatusCode(result.httpStatusCode());
    status.setCheckTimestamp(Instant.now());
    status.setMetadata(result.metadata());

    statusRepository.save(status);

    // To prevent the status table from growing indefinitely, delete all but the
    // oldest keepCount entries for the environment
    statusRepository.deleteAllButLatestByEnvironmentId(environment.getId(), this.keepCount);

    log.debug("Persisted status entry for environment {}", environment.getId());
  }
}
