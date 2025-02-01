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
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class GitHubDataSyncService {
  private final GitHubLabelSyncService gitHubLabelSyncService;
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
      GitHubCommitSyncService commitSyncService, GitHubLabelSyncService gitHubLabelSyncService) {
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
    this.gitHubLabelSyncService = gitHubLabelSyncService;
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
    var step1Start = Instant.now();
    log.info("[Step 1/10] Syncing Monitored Repositories...");
    var repositories = repositorySyncService.syncAllMonitoredRepositories();
    var step1Duration = Duration.between(step1Start, Instant.now()).toMillis();
    log.info("[Step 1/10] Completed Syncing {} repositories. "
        + "(Took: {} ms)", repositories.size(), step1Duration);

    // Sync all labels
    log.info("--------------------------------------------------");
    var step2Start = Instant.now();
    log.info("[Step 2/10] Syncing Labels...");
    gitHubLabelSyncService.syncLabelsOfAllRepositories(repositories);
    var step2Duration = Duration.between(step2Start, Instant.now()).toMillis();
    log.info("[Step 2/10] Completed Label Sync. (Took: {} ms)", step2Duration);


    // Sync pull requests
    log.info("--------------------------------------------------");
    var step3Start = Instant.now();
    log.info("[Step 3/10] Syncing Open Pull Requests...");
    pullRequestSyncService.syncOpenPullRequestsOfAllRepositories(repositories);
    var step3Duration = Duration.between(step3Start, Instant.now()).toMillis();
    log.info("[Step 3/10] Completed Pull Request Sync. (Took: {} ms)", step3Duration);

    // Sync environments
    log.info("--------------------------------------------------");
    var step4Start = Instant.now();
    log.info("[Step 4/10] Syncing Environments...");
    environmentSyncService.syncEnvironmentsOfAllRepositories(repositories);
    var step4Duration = Duration.between(step4Start, Instant.now()).toMillis();
    log.info("[Step 4/10] Completed Environment Sync. (Took: {} ms)", step4Duration);

    // Sync deployments
    log.info("--------------------------------------------------");
    var step5Start = Instant.now();
    log.info("[Step 5/10] Syncing Deployments (Cutoff: {})", cutoffDate);
    deploymentSyncService.syncDeploymentsOfAllRepositories(repositories, Optional.of(cutoffDate));
    var step5Duration = Duration.between(step5Start, Instant.now()).toMillis();
    log.info("[Step 5/10] Completed Deployment Sync. (Took: {} ms)", step5Duration);

    // Sync users
    log.info("--------------------------------------------------");
    var step6Start = Instant.now();
    log.info("[Step 6/10] Syncing Users...");
    userSyncService.syncAllExistingUsers();
    var step6Duration = Duration.between(step6Start, Instant.now()).toMillis();
    log.info("[Step 6/10] Completed User Sync. (Took: {} ms)", step6Duration);

    // Sync workflows
    log.info("--------------------------------------------------");
    var step7Start = Instant.now();
    log.info("[Step 7/10] Syncing Workflows...");
    workflowSyncService.syncWorkflowsOfAllRepositories(repositories);
    var step7Duration = Duration.between(step7Start, Instant.now()).toMillis();
    log.info("[Step 7/10] Completed Workflow Sync. (Took: {} ms)", step7Duration);

    // Sync branches
    log.info("--------------------------------------------------");
    var step8Start = Instant.now();
    log.info("[Step 8/10] Syncing Branches...");
    branchSyncService.syncBranchesOfAllRepositories(repositories);
    var step8Duration = Duration.between(step8Start, Instant.now()).toMillis();
    log.info("[Step 8/10] Completed Branch Sync. (Took: {} ms)", step8Duration);

    // Sync commits
    log.info("--------------------------------------------------");
    var step9Start = Instant.now();
    log.info("[Step 9/10] Syncing Commits...");
    commitSyncService.syncCommitsOfAllRepositories(repositories);
    var step9Duration = Duration.between(step9Start, Instant.now()).toMillis();
    log.info("[Step 9/10] Completed Commit Sync. (Took: {} ms)", step9Duration);

    // Sync workflow runs
    log.info("--------------------------------------------------");
    var step10Start = Instant.now();
    log.info("[Step 10/10] Syncing Workflow Runs (Cutoff: {})", cutoffDate);
    workflowRunSyncService.syncLatestRunsOfWorkflowsOfAllRepositories(repositories);
    var step10Duration = Duration.between(step10Start, Instant.now()).toMillis();
    log.info("[Step 10/10] Completed Workflow Run Sync. (Took: {} ms)", step10Duration);

    var endTime = OffsetDateTime.now();
    log.info("--------------------------------------------------");
    log.info("        Data Sync Job Completed Successfully");
    log.info("--------------------------------------------------");
    log.info("Step 1 took: {} ms", step1Duration);
    log.info("Step 2 took: {} ms", step2Duration);
    log.info("Step 3 took: {} ms", step3Duration);
    log.info("Step 4 took: {} ms", step4Duration);
    log.info("Step 5 took: {} ms", step5Duration);
    log.info("Step 6 took: {} ms", step6Duration);
    log.info("Step 7 took: {} ms", step7Duration);
    log.info("Step 8 took: {} ms", step8Duration);
    log.info("Step 9 took: {} ms", step9Duration);
    log.info("Step 10 took: {} ms", step10Duration);
    log.info("Total Duration: {} seconds", Duration.between(startTime, endTime).getSeconds());
    log.info("--------------------------------------------------");

    // Store successful sync status
    var syncStatus = new DataSyncStatus();
    syncStatus.setStartTime(startTime);
    syncStatus.setEndTime(endTime);
    dataSyncStatusRepository.save(syncStatus);
  }
}
