package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.environment.Environment;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    // We need to return a CompletableFuture to allow the caller to wait for the
    // completion of the status check, even though we don't need to return any value.
    final CompletableFuture<Void> returnFuture = CompletableFuture.completedFuture(null);

    final StatusCheckType checkType = environment.getStatusCheckType();

    log.debug("Starting status check for environment {} (ID: {}) with type {}",
        environment.getName(), environment.getId(), checkType);

    if (checkType == null) {
      log.warn("Skipping environment {} - no check type configured", environment.getId());
      return returnFuture;
    }

    final StatusCheckStrategy strategy = checkStrategies.get(checkType);

    if (strategy == null) {
      log.error("No strategy found for check type {} in environment {}",
          checkType, environment.getId());
      return returnFuture;
    }

    final StatusCheckResult result = strategy.check(environment);
    log.debug("Check completed for environment {} - success: {}, code: {}",
        environment.getId(), result.success(), result.httpStatusCode());

    saveStatusResult(environment, result);

    return returnFuture;
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
