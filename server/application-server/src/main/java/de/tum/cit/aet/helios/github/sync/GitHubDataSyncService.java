package de.tum.cit.aet.helios.github.sync;


import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentSyncService;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentSyncService;
import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.cit.aet.helios.branch.github.GitHubBranchSyncService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowRunSyncService;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowSyncService;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

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

    public GitHubDataSyncService(
            DataSyncStatusRepository dataSyncStatusRepository, GitHubUserSyncService userSyncService,
            GitHubRepositorySyncService repositorySyncService,
            GitHubPullRequestSyncService pullRequestSyncService,
            GitHubWorkflowRunSyncService workflowRunSyncService, GitHubWorkflowSyncService workflowSyncService,
            GitHubBranchSyncService branchSyncService,
            GitHubEnvironmentSyncService environmentSyncService, GitHubDeploymentSyncService deploymentSyncService) {
        this.dataSyncStatusRepository = dataSyncStatusRepository;
        this.userSyncService = userSyncService;
        this.repositorySyncService = repositorySyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.workflowRunSyncService = workflowRunSyncService;
        this.workflowSyncService = workflowSyncService;
        this.branchSyncService = branchSyncService;
        this.environmentSyncService = environmentSyncService;
        this.deploymentSyncService = deploymentSyncService;
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
            log.info("Skipping sync, last sync was less than {} minutes ago", runOnStartupCooldownInMinutes);
            return;
        }

        log.info("--------------------------------------------------");
        log.info("        Starting Data Sync Job");
        log.info("--------------------------------------------------");

        // Start new sync
        var startTime = OffsetDateTime.now();

        log.info("--------------------------------------------------");
        log.info("[Step 1/8] Syncing Monitored Repositories...");
        var repositories = repositorySyncService.syncAllMonitoredRepositories();
        log.info("[Step 1/8] Completed Syncing {} repositories", repositories.size());


        // Sync pull requests
        log.info("--------------------------------------------------");
        log.info("[Step 2/8] Syncing Pull Requests (Cutoff: {})", cutoffDate);
        pullRequestSyncService.syncPullRequestsOfAllRepositories(repositories, Optional.of(cutoffDate));
        log.info("[Step 2/8] Completed Pull Request Sync");


        // Sync environments
        log.info("--------------------------------------------------");
        log.info("[Step 3/8] Syncing Environments for Repositories...");
        environmentSyncService.syncEnvironmentsOfAllRepositories(repositories);
        log.info("[Step 3/8] Completed Environment Sync");


        // Sync deployments
        log.info("--------------------------------------------------");
        log.info("[Step 4/8] Syncing Deployments for Repositories...");
        deploymentSyncService.syncDeploymentsOfAllRepositories(repositories);
        log.info("[Step 4/8] Completed Deployment Sync");

        // Sync users
        log.info("--------------------------------------------------");
        log.info("[Step 5/8] Syncing Users...");
        userSyncService.syncAllExistingUsers();
        log.info("[Step 5/8] Completed User Sync");

        // Sync workflows
        log.info("--------------------------------------------------");
        log.info("[Step 6/8] Syncing Workflows");
        workflowSyncService.syncWorkflowsOfAllRepositories(repositories);
        log.info("[Step 6/8] Completed Workflow Sync");

        // Sync workflow runs
        log.info("--------------------------------------------------");
        log.info("[Step 7/8] Syncing WorkflowRuns (Cutoff: {})", cutoffDate);
        workflowRunSyncService.syncRunsOfAllRepositories(repositories, Optional.of(cutoffDate));
        log.info("[Step 7/8] Completed WorkflowRun Sync");

        // Sync branches
        log.info("--------------------------------------------------");
        log.info("[Step 8/8] Syncing Branches...");
        branchSyncService.syncBranchesOfAllRepositories(repositories);
        log.info("[Step 8/8] Completed Branch Sync");


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
