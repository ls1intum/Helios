package de.tum.cit.aet.helios.github.sync;

import de.tum.cit.aet.helios.branch.github.GitHubBranchSyncService;
import de.tum.cit.aet.helios.commit.github.GitHubCommitSyncService;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentSyncService;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentSyncService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowRunSyncService;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowSyncService;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Log4j2
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

  public GitHubDataSyncService(
      DataSyncStatusRepository dataSyncStatusRepository,
      GitHubUserSyncService userSyncService,
      GitHubRepositorySyncService repositorySyncService,
      GitHubPullRequestSyncService pullRequestSyncService,
      GitHubWorkflowRunSyncService workflowRunSyncService,
      GitHubWorkflowSyncService workflowSyncService,
      GitHubBranchSyncService branchSyncService,
      GitHubEnvironmentSyncService environmentSyncService,
      GitHubDeploymentSyncService deploymentSyncService,
      GitHubCommitSyncService commitSyncService) {
    this.dataSyncStatusRepository = dataSyncStatusRepository;
    this.userSyncService = userSyncService;
    this.repositorySyncService = repositorySyncService;
    this.pullRequestSyncService = pullRequestSyncService;
    this.workflowRunSyncService = workflowRunSyncService;
    this.workflowSyncService = workflowSyncService;
    this.branchSyncService = branchSyncService;
    this.environmentSyncService = environmentSyncService;
    this.deploymentSyncService = deploymentSyncService;
    this.commitSyncService = commitSyncService;
  }

  @Transactional
  public void syncData() {
    var cutoffDate = OffsetDateTime.now().minusDays(timeframe);

    // Get last sync time
    var lastSync = dataSyncStatusRepository.findTopByOrderByStartTimeDesc();
    if (lastSync.isPresent()) {
      var lastSyncTime = lastSync.get().getStartTime();
      cutoffDate = lastSyncTime.isAfter(cutoffDate) ? lastSyncTime : cutoffDate;
    }

    var cooldownTime = OffsetDateTime.now().minusMinutes(runOnStartupCooldownInMinutes);
    if (lastSync.isPresent() && lastSync.get().getStartTime().isAfter(cooldownTime)) {
      log.info(
          "Skipping sync, last sync was less than {} minutes ago", runOnStartupCooldownInMinutes);
      return;
    }

    log.info("--------------------------------------------------");
    log.info("        Starting Data Sync Job");
    log.info("--------------------------------------------------");

    // Start new sync
    // CHECKSTYLE.OFF: VariableDeclarationUsageDistance
    var startTime = OffsetDateTime.now();
    // CHECKSTYLE.ON: VariableDeclarationUsageDistance

    log.info("--------------------------------------------------");
    log.info("[Step 1/9] Syncing Monitored Repositories...");
    var repositories = repositorySyncService.syncAllMonitoredRepositories();
    log.info("[Step 1/9] Completed Syncing {} repositories", repositories.size());

    // Sync pull requests
    log.info("--------------------------------------------------");
    log.info("[Step 2/9] Syncing Pull Requests (Cutoff: {})", cutoffDate);
    pullRequestSyncService.syncPullRequestsOfAllRepositories(repositories, Optional.of(cutoffDate));
    log.info("[Step 2/9] Completed Pull Request Sync");

    // Sync environments
    log.info("--------------------------------------------------");
    log.info("[Step 3/9] Syncing Environments for Repositories...");
    environmentSyncService.syncEnvironmentsOfAllRepositories(repositories);
    log.info("[Step 3/9] Completed Environment Sync");

    // Sync deployments
    log.info("--------------------------------------------------");
    log.info("[Step 4/9] Syncing Deployments for Repositories...");
    deploymentSyncService.syncDeploymentsOfAllRepositories(repositories);
    log.info("[Step 4/9] Completed Deployment Sync");

    // Sync users
    log.info("--------------------------------------------------");
    log.info("[Step 5/9] Syncing Users...");
    userSyncService.syncAllExistingUsers();
    log.info("[Step 5/9] Completed User Sync");

    // Sync workflows
    log.info("--------------------------------------------------");
    log.info("[Step 6/9] Syncing Workflows");
    workflowSyncService.syncWorkflowsOfAllRepositories(repositories);
    log.info("[Step 6/9] Completed Workflow Sync");

    // Sync workflow runs
    log.info("--------------------------------------------------");
    log.info("[Step 7/9] Syncing WorkflowRuns (Cutoff: {})", cutoffDate);
    workflowRunSyncService.syncRunsOfAllRepositories(repositories, Optional.of(cutoffDate));
    log.info("[Step 7/9] Completed WorkflowRun Sync");

    // Sync branches
    log.info("--------------------------------------------------");
    log.info("[Step 8/9] Syncing Branches...");
    branchSyncService.syncBranchesOfAllRepositories(repositories);
    log.info("[Step 8/9] Completed Branch Sync");

    // Sync commits
    log.info("--------------------------------------------------");
    log.info("[Step 9/9] Syncing Commits...");
    commitSyncService.syncCommitsOfAllRepositories(repositories);
    log.info("[Step 9/9] Completed Commit Sync");

    var endTime = OffsetDateTime.now();
    log.info("--------------------------------------------------");
    log.info("        Data Sync Job Completed Successfully");
    log.info("--------------------------------------------------");
    log.info("Total Duration: {} seconds", Duration.between(startTime, endTime).getSeconds());
    log.info("--------------------------------------------------");
    // Store successful sync status
    var syncStatus = new DataSyncStatus();
    syncStatus.setStartTime(startTime);
    syncStatus.setEndTime(endTime);
    dataSyncStatusRepository.save(syncStatus);
  }
}
