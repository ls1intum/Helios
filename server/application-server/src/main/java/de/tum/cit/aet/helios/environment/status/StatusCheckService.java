package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Log4j2
public class StatusCheckService {
  private final Map<StatusCheckType, StatusCheckStrategy> checkStrategies;
  private final EnvironmentService environmentService;
  private final EnvironmentStatusRepository statusRepository;
  private final TransactionTemplate transactionTemplate;
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
   * <p>The type of status check to be performed is determined by the environment's
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
    })
    .orTimeout(this.config.getCheckInterval().getSeconds(), TimeUnit.SECONDS)
    .exceptionally(ex -> {
      handleThrowable(environment, ex);
      return null;
    });
  }

  private void handleThrowable(Environment env, Throwable ex) {
    log.error("Failed to perform status check for environment {}", env.getId(), ex);

    // Status code of 0 indicates that the check failed because of us
    saveStatusResult(env, new StatusCheckResult(false, 0, Map.of()));
  }

  private void saveStatusResult(Environment environment, StatusCheckResult result) {
    // We need to use a TransactionTemplate here because this runs asynchronously
    transactionTemplate.executeWithoutResult(transactionStatus -> {
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

      Optional<EnvironmentStatus> latestStatus = environment.getLatestStatus();

      // Did the status change? If so, update the statusChangedAt field
      if (
          !latestStatus.isPresent()
          || latestStatus.get().getHttpStatusCode() != result.httpStatusCode()
      ) {
        environmentService.markStatusAsChanged(environment);
      }

      log.debug("Persisted status entry for environment {}", environment.getId());
    });
  }
}
