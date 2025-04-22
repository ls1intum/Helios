package de.tum.cit.aet.helios.workflow.cleanup;

import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * This class is responsible for cleaning up obsolete workflow runs based on configured policies.
 * It is scheduled to run every day at 1 AM.
 *
 * <p>It uses the {@link WorkflowRunRepository} to perform the actual deletion of obsolete runs.
 * The policies are defined in the {@link WorkflowRunCleanupProps} class.
 */
@RequiredArgsConstructor
@Component
@Log4j2
public class WorkflowRunCleanupTask {

  private final WorkflowRunRepository repo;
  private final WorkflowRunCleanupProps props;


  /**
   * This method is scheduled to run every day at 1 AM. It purges workflow runs based on the
   * configured policies.
   */
  @Scheduled(cron = "${cleanup.workflow-run.cron:0 0 1 * * *}")
  @Transactional
  public void purge() {
    log.info("Workflow‑run cleanup started.");
    int totalDeleted = 0;

    for (WorkflowRunCleanupProps.Policy policy : props.getPolicies()) {

      int deleted = repo.purgeObsoleteRuns(
          policy.getKeep(),
          policy.getAgeDays(),
          policy.getTestProcessingStatus()
      );

      log.info("Cleanup policy  tps={}  keep={}  ageDays={}  →  {} rows deleted",
          policy.getTestProcessingStatus(), policy.getKeep(),
          policy.getAgeDays(), deleted);

      totalDeleted += deleted;
    }

    log.info("Workflow‑run cleanup finished.  Total rows deleted: {}", totalDeleted);
  }
}