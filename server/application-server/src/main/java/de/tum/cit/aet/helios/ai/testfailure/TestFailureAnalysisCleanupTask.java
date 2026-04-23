package de.tum.cit.aet.helios.ai.testfailure;

import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Log4j2
@RequiredArgsConstructor
class TestFailureAnalysisCleanupTask {

  private final TestFailureAnalysisRepository repository;

  @Value("${helios.ai.test-failure.cleanup.max-age:14d}")
  private Duration maxAge;

  @Value("${helios.ai.test-failure.cleanup.dry-run:false}")
  private boolean dryRun;

  @Scheduled(cron = "${helios.ai.test-failure.cleanup.cron:0 0 3 * * *}")
  @Transactional
  void purge() {
    OffsetDateTime cutoff = currentTime().minus(maxAge);
    long candidates = repository.countByAnalyzedAtBefore(cutoff);

    if (dryRun) {
      log.info(
          "DRY-RUN: AI test-failure analysis cleanup found {} rows analyzed before {}",
          candidates,
          cutoff);
      return;
    }

    long deleted = repository.deleteByAnalyzedAtBefore(cutoff);
    log.info(
        "DELETE: AI test-failure analysis cleanup deleted {} rows analyzed before {}",
        deleted,
        cutoff);
  }

  private OffsetDateTime currentTime() {
    return OffsetDateTime.now();
  }
}
