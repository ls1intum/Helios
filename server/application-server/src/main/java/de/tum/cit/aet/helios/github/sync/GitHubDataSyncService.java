package de.tum.cit.aet.helios.github.sync;


import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.cit.aet.helios.branch.github.GitHubBranchSyncService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowSyncService;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private final GitHubWorkflowSyncService workflowSyncService;
    private final GitHubBranchSyncService branchSyncService;

    public GitHubDataSyncService(
            DataSyncStatusRepository dataSyncStatusRepository, GitHubUserSyncService userSyncService,
            GitHubRepositorySyncService repositorySyncService,
            GitHubPullRequestSyncService pullRequestSyncService,
            GitHubWorkflowSyncService workflowSyncService,
            GitHubBranchSyncService branchSyncService) {
        this.dataSyncStatusRepository = dataSyncStatusRepository;
        this.userSyncService = userSyncService;
        this.repositorySyncService = repositorySyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.workflowSyncService = workflowSyncService;
        this.branchSyncService = branchSyncService;
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

        // Start new sync
        var startTime = OffsetDateTime.now();

        var repositories = repositorySyncService.syncAllMonitoredRepositories();
        
        pullRequestSyncService.syncPullRequestsOfAllRepositories(repositories, Optional.of(cutoffDate));
        userSyncService.syncAllExistingUsers();
        workflowSyncService.syncRunsOfAllRepositories(repositories, Optional.of(cutoffDate));
        branchSyncService.syncBranchesOfAllRepositories(repositories);

        var endTime = OffsetDateTime.now();

        // Store successful sync status
        var syncStatus = new DataSyncStatus();
        syncStatus.setStartTime(startTime);
        syncStatus.setEndTime(endTime);
        dataSyncStatusRepository.save(syncStatus);
    }
}
