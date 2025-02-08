package de.tum.cit.aet.helios.github.sync;

import de.tum.cit.aet.helios.branch.github.GitHubBranchSyncService;
import de.tum.cit.aet.helios.commit.github.GitHubCommitSyncService;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentSyncService;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentSyncService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.label.github.GitHubLabelSyncService;
import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowRunSyncService;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowSyncService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
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
  private final GitHubUserSyncService userSyncService;
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubPullRequestSyncService pullRequestSyncService;
  private final GitHubWorkflowRunSyncService workflowRunSyncService;
  private final GitHubWorkflowSyncService workflowSyncService;
  private final GitHubBranchSyncService branchSyncService;
  private final GitHubEnvironmentSyncService environmentSyncService;
  private final GitHubDeploymentSyncService deploymentSyncService;
  private final GitHubCommitSyncService commitSyncService;
  private final GitHubLabelSyncService gitHubLabelSyncService;


  @Transactional
  public void syncRepositories(List<String> nameWithOwners) {
    for (String repositoryNameWithOwner : nameWithOwners) {
      log.info("Started syncing repository: {}", repositoryNameWithOwner);
      Optional<GHRepository> optionalRepository =
          repositorySyncService.syncRepository(repositoryNameWithOwner);
      if (optionalRepository.isEmpty()) {
        log.error("Failed to sync repository: {}", repositoryNameWithOwner);
        continue;
      }
      log.info("Successfully synced repository: {}", repositoryNameWithOwner);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    log.info("--------------------------------------------------");
    log.info("    Starting Data Sync Job for Repository: {}", repositoryNameWithOwner);
    log.info("--------------------------------------------------");

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

      log.info("--------------------------------------------------");
      var step1Start = Instant.now();
      log.info("Repository: {} --> [Step 1/10] Syncing Repository", repositoryNameWithOwner);
      Optional<GHRepository> optionalRepository =
          repositorySyncService.syncRepository(repositoryNameWithOwner);
      var step1Duration = Duration.between(step1Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 1/10] Completed Syncing "
          + "(Took: {} ms)", repositoryNameWithOwner, step1Duration);

      if (optionalRepository.isEmpty()) {
        log.error(
            "Repository: {} --> [Step 1/10] Syncing Repository Failed. "
                + "Skipping the rest of the sync steps.",
            repositoryNameWithOwner);
        syncStatus.setEndTime(OffsetDateTime.now());
        syncStatus.setStatus(DataSyncStatus.Status.FAILED);
        dataSyncStatusRepository.save(syncStatus);
        return;
      }

      GHRepository ghRepository = optionalRepository.get();

      // Sync all labels
      log.info("--------------------------------------------------");
      var step2Start = Instant.now();
      log.info("Repository: {} --> [Step 2/10] Syncing Labels...", repositoryNameWithOwner);
      gitHubLabelSyncService.syncLabelsOfRepository(ghRepository);
      var step2Duration = Duration.between(step2Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 2/10] Completed Label Sync. (Took: {} ms)",
          repositoryNameWithOwner, step2Duration);


      // Sync pull requests
      log.info("--------------------------------------------------");
      var step3Start = Instant.now();
      log.info("Repository: {} --> [Step 3/10] Syncing Open Pull Requests...",
          repositoryNameWithOwner);
      pullRequestSyncService.syncPullRequestsOfRepository(ghRepository);
      var step3Duration = Duration.between(step3Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 3/10] Completed Pull Request Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step3Duration);

      // Sync environments
      log.info("--------------------------------------------------");
      var step4Start = Instant.now();
      log.info("Repository: {} --> [Step 4/10] Syncing Environments...", repositoryNameWithOwner);
      environmentSyncService.syncEnvironmentsOfRepository(ghRepository);
      var step4Duration = Duration.between(step4Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 4/10] Completed Environment Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step4Duration);

      // Sync deployments
      log.info("--------------------------------------------------");
      var step5Start = Instant.now();
      log.info("Repository: {} --> [Step 5/10] Syncing Deployments (Cutoff: {})",
          repositoryNameWithOwner, cutoffDate);
      deploymentSyncService.syncDeploymentsOfRepository(ghRepository, Optional.of(cutoffDate));
      var step5Duration = Duration.between(step5Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 5/10] Completed Deployment Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step5Duration);

      // Sync users
      log.info("--------------------------------------------------");
      var step6Start = Instant.now();
      log.info("Repository: {} --> [Step 6/10] Syncing Users...", repositoryNameWithOwner);
      userSyncService.syncAllExistingUsers();
      var step6Duration = Duration.between(step6Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 6/10] Completed User Sync. (Took: {} ms)",
          repositoryNameWithOwner, step6Duration);

      // Sync workflows
      log.info("--------------------------------------------------");
      var step7Start = Instant.now();
      log.info("Repository: {} --> [Step 7/10] Syncing Workflows...", repositoryNameWithOwner);
      workflowSyncService.syncWorkflowsOfRepository(ghRepository);
      var step7Duration = Duration.between(step7Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 7/10] Completed Workflow Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step7Duration);

      // Sync branches
      log.info("--------------------------------------------------");
      var step8Start = Instant.now();
      log.info("Repository: {} --> [Step 8/10] Syncing Branches...", repositoryNameWithOwner);
      branchSyncService.syncBranchesOfRepository(ghRepository);
      var step8Duration = Duration.between(step8Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 8/10] Completed Branch Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step8Duration);

      // Sync commits
      log.info("--------------------------------------------------");
      var step9Start = Instant.now();
      log.info("Repository: {} --> [Step 9/10] Syncing Commits...", repositoryNameWithOwner);
      commitSyncService.syncCommitsOfRepository(ghRepository);
      var step9Duration = Duration.between(step9Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 9/10] Completed Commit Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step9Duration);

      // Sync workflow runs
      log.info("--------------------------------------------------");
      var step10Start = Instant.now();
      log.info("Repository: {} --> [Step 10/10] Syncing Workflow Runs (Cutoff: {})",
          repositoryNameWithOwner, cutoffDate);
      workflowRunSyncService.syncRunsOfRepository(ghRepository, Optional.of(cutoffDate));
      var step10Duration = Duration.between(step10Start, Instant.now()).toMillis();
      log.info("Repository: {} --> [Step 10/10] Completed Workflow Run Sync. (Took: {} ms)",
          repositoryNameWithOwner,
          step10Duration);

      var endTime = OffsetDateTime.now();

      log.info("--------------------------------------------------");
      log.info("    Data Sync Job Completed Successfully for Repository: {}",
          repositoryNameWithOwner);
      log.info("--------------------------------------------------");
      log.info("Repository: {} --> Step 1 took: {} ms", repositoryNameWithOwner, step1Duration);
      log.info("Repository: {} --> Step 2 took: {} ms", repositoryNameWithOwner, step2Duration);
      log.info("Repository: {} --> Step 3 took: {} ms", repositoryNameWithOwner, step3Duration);
      log.info("Repository: {} --> Step 4 took: {} ms", repositoryNameWithOwner, step4Duration);
      log.info("Repository: {} --> Step 5 took: {} ms", repositoryNameWithOwner, step5Duration);
      log.info("Repository: {} --> Step 6 took: {} ms", repositoryNameWithOwner, step6Duration);
      log.info("Repository: {} --> Step 7 took: {} ms", repositoryNameWithOwner, step7Duration);
      log.info("Repository: {} --> Step 8 took: {} ms", repositoryNameWithOwner, step8Duration);
      log.info("Repository: {} --> Step 9 took: {} ms", repositoryNameWithOwner, step9Duration);
      log.info("Repository: {} --> Step 10 took: {} ms", repositoryNameWithOwner, step10Duration);
      log.info("Repository: {} --> Total Duration: {} seconds", repositoryNameWithOwner,
          Duration.between(startTime, endTime).getSeconds());
      log.info("--------------------------------------------------");
      log.info("--------------------------------------------------");

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
}
