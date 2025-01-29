package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@EnableAsync
@Log4j2
public class EnvironmentStatusChecker {
  private final EnvironmentRepository environmentRepository;
  private final EnvironmentStatusRepository statusRepository;
  private final RestTemplate restTemplate;

  @Scheduled(fixedRate = 120_000)
  @Async("statusCheckTaskExecutor")
  public void checkStatusUrls() {
    log.info("Starting scheduled status check");

    List<Environment> environments = environmentRepository.findAllWithStatusUrl();
    log.debug("Found {} environments with status URLs", environments.size());

    environments.forEach(env -> {
      log.debug("Checking environment {} (ID: {})", env.getName(), env.getId());
      checkAndUpdateStatus(env);
    });

    log.info("Completed status check for {} environments", environments.size());
  }

  private void checkAndUpdateStatus(Environment env) {
    EnvironmentStatus status = new EnvironmentStatus();
    status.setCheckTimestamp(Instant.now());

    try {
      log.debug("Calling status URL: {}", env.getStatusUrl());
      ResponseEntity<Void> response = restTemplate.getForEntity(
          env.getStatusUrl(),
          Void.class);
      status.setStatusCode(response.getStatusCode().value());
      log.info("Status check successful for environment {}: HTTP {}",
          env.getId(), status.getStatusCode());
    } catch (Exception e) {
      status.setStatusCode(503); // Service Unavailable
      log.warn("Status check failed for environment {} ({}): {}",
          env.getId(), env.getStatusUrl(), e.getMessage());
      log.debug("Stack trace for failed check:", e);
    }

    status.setCheckTimestamp(Instant.now());
    env.addStatusEntry(status);
    statusRepository.save(status);
    log.debug("Persisted status entry for environment {}", env.getId());
  }
}
