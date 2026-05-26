package de.tum.cit.aet.helios.workflow.cleanup;

import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
   * This method is called when the application is ready. It logs the current mode of operation
   * (DRY-RUN or DELETE) based on the configuration.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (props.isDryRun()) {
      log.info("Workflow‑run cleanup is in DRY-RUN mode. No rows will be deleted.");
    } else {
      log.info("Workflow‑run cleanup is in DELETE mode.");
    }
  }


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

      String tps = policy.getTestProcessingStatus();
      if (tps != null && tps.trim().isEmpty()) {
        tps = null;
      }

      int deleted = 0;

      if (props.isDryRun()) {
        List<Long> ids = repo.previewObsoleteRunIds(
            policy.getKeep(), policy.getAgeDays(), tps);

        deleted = ids.size();

        log.info(
            "DRY-RUN: Cleanup policy tps={} keep={} ageDays={}  →  {} rows will be deleted",
            tps, policy.getKeep(), policy.getAgeDays(), ids.size());

        List<Long> idsOfSurvivingRuns = repo.previewSurvivorRunIds(
            policy.getKeep(), policy.getAgeDays(), tps);

        log.info(
            "DRY-RUN: Cleanup policy tps={} keep={} ageDays={}  →  {} rows will survive: {}",
            tps, policy.getKeep(), policy.getAgeDays(), idsOfSurvivingRuns.size(),
            idsOfSurvivingRuns);

      } else {
        deleted = repo.purgeObsoleteRuns(
            policy.getKeep(),
            policy.getAgeDays(),
            tps
        );

        log.info("DELETE: Cleanup policy  tps={}  keep={}  ageDays={}  →  {} rows deleted",
            tps, policy.getKeep(),
            policy.getAgeDays(), deleted);
      }

      totalDeleted += deleted;
    }

    log.info("Workflow‑run cleanup finished.  Total rows deleted: {}", totalDeleted);
  }


  /**
   * Sweeps workflow runs whose {@code head_branch} no longer exists in the
   * {@code branch} table — typically feature branches deleted after a PR
   * merged. The keep-N policy in {@link #purge()} cannot detect these,
   * so they would otherwise accumulate indefinitely.
   *
   * <p>Runs daily at 01:30 (30 minutes after {@link #purge()}) and honours
   * the same {@link WorkflowRunCleanupProps#isDryRun()} flag. Runs younger
   * than {@code graceDays} are skipped to avoid races with branch-sync
   * state. Runs still referenced by a {@code helios_deployment} or
   * {@code deployment} row are skipped to preserve the deployment → build
   * audit link.
   */
  @Scheduled(cron = "${cleanup.workflow-run.orphan-branches.cron:0 30 1 * * *}")
  @Transactional
  public void purgeOrphanBranchRuns() {
    WorkflowRunCleanupProps.OrphanBranches cfg = props.getOrphanBranches();
    if (!cfg.isEnabled()) {
      log.debug("Orphan-branch cleanup skipped (disabled).");
      return;
    }

    int graceDays = cfg.getGraceDays();
    log.info("Orphan-branch cleanup started (graceDays={}).", graceDays);

    if (props.isDryRun()) {
      List<Long> ids = repo.previewOrphanBranchRunIds(graceDays);
      log.info("DRY-RUN: Orphan-branch cleanup graceDays={}  →  {} rows would be deleted",
          graceDays, ids.size());
    } else {
      int deleted = repo.purgeOrphanBranchRuns(graceDays);
      log.info("DELETE: Orphan-branch cleanup graceDays={}  →  {} rows deleted",
          graceDays, deleted);
    }

    log.info("Orphan-branch cleanup finished.");
  }
}
