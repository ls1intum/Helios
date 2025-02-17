package de.tum.cit.aet.helios.github.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubDataSyncService {
  @Value("${monitoring.timeframe}")
  private int timeframe;

  @Value("${monitoring.runOnStartupCooldownInMinutes}")
  private int runOnStartupCooldownInMinutes;

  private final DataSyncStatusRepository dataSyncStatusRepository;
  private final GitHubDataSyncOrchestrator dataSyncOrchestrator;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void syncUsers() {
    logSeparator();
    log.info("    Starting User Sync Job");
    logSeparator();
    var start = Instant.now();
    log.info("    Syncing Users...");
    dataSyncOrchestrator.syncAllExistingUsers();
    var duration = Duration.between(start, Instant.now()).toMillis();
    logSeparator();
    log.info("    User Sync Completed. (Took: {} ms)",
        duration);
    logSeparator();
  }


  public void syncRepositoryData(String repositoryNameWithOwner) {
    var cutoffDate = OffsetDateTime.now().minusDays(timeframe);

    // Get last sync time
    var lastSync = dataSyncStatusRepository.findTopByRepositoryNameWithOwner(
        repositoryNameWithOwner);
    if (lastSync.isPresent()) {
      var lastSyncTime = lastSync.get().getStartTime();
      cutoffDate = lastSyncTime.isAfter(cutoffDate) ? lastSyncTime : cutoffDate;
    }

    var cooldownTime = OffsetDateTime.now().minusMinutes(runOnStartupCooldownInMinutes);
    if (lastSync.isPresent() && lastSync.get().getStartTime().isAfter(cooldownTime)) {
      log.info(
          "Skipping sync for repository {}, last sync was less than {} minutes ago",
          repositoryNameWithOwner, runOnStartupCooldownInMinutes);
      return;
    }

    logSeparator();
    log.info("    Starting Data Sync Job for Repository: {}", repositoryNameWithOwner);
    logSeparator();

    // Create a new sync status record with IN_PROGRESS status.
    DataSyncStatus syncStatus = new DataSyncStatus();
    syncStatus.setRepositoryNameWithOwner(repositoryNameWithOwner);
    syncStatus.setStartTime(OffsetDateTime.now());
    syncStatus.setStatus(DataSyncStatus.Status.IN_PROGRESS);
    dataSyncStatusRepository.save(syncStatus);

    try {

      // Start new sync
      // CHECKSTYLE.OFF: VariableDeclarationUsageDistance
      var startTime = OffsetDateTime.now();
      // CHECKSTYLE.ON: VariableDeclarationUsageDistance

      logSeparator();
      var step1Start = Instant.now();
      log.info("Repository: {} --> [Step 1/9] Syncing Repository", repositoryNameWithOwner);
      Optional<GHRepository> optionalRepository =
          dataSyncOrchestrator.syncRepository(repositoryNameWithOwner);
      var step1Duration = Duration.between(step1Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 1/9] Completed Syncing "
          + "(Took: {} ms)", repositoryNameWithOwner, step1Duration);

      if (optionalRepository.isEmpty()) {
        log.error(
            "Repository: {} --> [Step 1/9] Syncing Repository Failed. "
                + "Skipping the rest of the sync steps.",
            repositoryNameWithOwner);
        syncStatus.setEndTime(OffsetDateTime.now());
        syncStatus.setStatus(DataSyncStatus.Status.FAILED);
        dataSyncStatusRepository.save(syncStatus);
        return;
      }

      GHRepository ghRepository = optionalRepository.get();

      // Sync all labels
      logSeparator();
      var step2Start = Instant.now();
      log.info("Repository: {} --> [Step 2/9] Syncing Labels...", repositoryNameWithOwner);
      dataSyncOrchestrator.syncLabelsOfRepository(ghRepository);
      var step2Duration = Duration.between(step2Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 2/9] Completed Label Sync. (Took: {} ms)",
          repositoryNameWithOwner, step2Duration);


      // Sync pull requests
      logSeparator();
      var step3Start = Instant.now();
      log.info("Repository: {} --> [Step 3/9] Syncing Open Pull Requests...",
          repositoryNameWithOwner);
      dataSyncOrchestrator.syncPullRequestsOfRepository(ghRepository);
      var step3Duration = Duration.between(step3Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 3/9] Completed Pull Request Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step3Duration);

      // Sync environments
      logSeparator();
      var step4Start = Instant.now();
      log.info("Repository: {} --> [Step 4/9] Syncing Environments...", repositoryNameWithOwner);
      dataSyncOrchestrator.syncEnvironmentsOfRepository(ghRepository);
      var step4Duration = Duration.between(step4Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 4/9] Completed Environment Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step4Duration);

      // Sync deployments
      logSeparator();
      var step5Start = Instant.now();
      log.info("Repository: {} --> [Step 5/9] Syncing Deployments (Cutoff: {})",
          repositoryNameWithOwner, cutoffDate);
      dataSyncOrchestrator.syncDeploymentsOfRepository(ghRepository, Optional.of(cutoffDate));
      var step5Duration = Duration.between(step5Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 5/9] Completed Deployment Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step5Duration);

      // Sync workflows
      logSeparator();
      var step6Start = Instant.now();
      log.info("Repository: {} --> [Step 6/9] Syncing Workflows...", repositoryNameWithOwner);
      dataSyncOrchestrator.syncWorkflowsOfRepository(ghRepository);
      var step6Duration = Duration.between(step6Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 6/9] Completed Workflow Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step6Duration);

      // Sync branches
      logSeparator();
      var step7Start = Instant.now();
      log.info("Repository: {} --> [Step 7/9] Syncing Branches...", repositoryNameWithOwner);
      dataSyncOrchestrator.syncBranchesOfRepository(ghRepository);
      var step7Duration = Duration.between(step7Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 7/9] Completed Branch Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step7Duration);

      // Sync commits
      logSeparator();
      var step8Start = Instant.now();
      log.info("Repository: {} --> [Step 8/9] Syncing Commits...", repositoryNameWithOwner);
      dataSyncOrchestrator.syncCommitsOfRepository(ghRepository);
      var step8Duration = Duration.between(step8Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 8/9] Completed Commit Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step8Duration);

      // Sync workflow runs
      logSeparator();
      var step9Start = Instant.now();
      log.info("Repository: {} --> [Step 9/9] Syncing Workflow Runs (Cutoff: {})",
          repositoryNameWithOwner, cutoffDate);
      dataSyncOrchestrator.syncRunsOfRepository(ghRepository, Optional.of(cutoffDate));
      var step9Duration = Duration.between(step9Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 9/9] Completed Workflow Run Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step9Duration);

      // CHECKSTYLE.OFF: VariableDeclarationUsageDistance
      var endTime = OffsetDateTime.now();
      // CHECKSTYLE.ON: VariableDeclarationUsageDistance

      logSeparator();
      log.info("    Data Sync Job Completed Successfully for Repository: {}",
          repositoryNameWithOwner);
      logSeparator();
      log.info("Repository: {} --> Step 1 took: {} ms", repositoryNameWithOwner, step1Duration);
      log.info("Repository: {} --> Step 2 took: {} ms", repositoryNameWithOwner, step2Duration);
      log.info("Repository: {} --> Step 3 took: {} ms", repositoryNameWithOwner, step3Duration);
      log.info("Repository: {} --> Step 4 took: {} ms", repositoryNameWithOwner, step4Duration);
      log.info("Repository: {} --> Step 5 took: {} ms", repositoryNameWithOwner, step5Duration);
      log.info("Repository: {} --> Step 6 took: {} ms", repositoryNameWithOwner, step6Duration);
      log.info("Repository: {} --> Step 7 took: {} ms", repositoryNameWithOwner, step7Duration);
      log.info("Repository: {} --> Step 8 took: {} ms", repositoryNameWithOwner, step8Duration);
      log.info("Repository: {} --> Step 9 took: {} ms", repositoryNameWithOwner, step9Duration);
      log.info("Repository: {} --> Total Duration: {} seconds", repositoryNameWithOwner,
          Duration.between(startTime, endTime).getSeconds());
      logSeparator();
      logSeparator();

      // Store successful sync status
      syncStatus.setEndTime(endTime);
      syncStatus.setStatus(DataSyncStatus.Status.SUCCESS);
      dataSyncStatusRepository.save(syncStatus);
    } catch (Exception e) {
      log.error("Repository: {} --> Error syncing repository: {}",
          repositoryNameWithOwner,
          e.getMessage(), e);
      // Update the sync record with FAILED status.
      syncStatus.setEndTime(OffsetDateTime.now());
      syncStatus.setStatus(DataSyncStatus.Status.FAILED);
      dataSyncStatusRepository.save(syncStatus);
    }
  }


  private void logSeparator() {
    log.info("--------------------------------------------------");
  }
}
