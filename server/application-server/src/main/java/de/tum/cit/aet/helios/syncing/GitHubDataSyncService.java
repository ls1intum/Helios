package de.tum.cit.aet.helios.syncing;


import de.tum.cit.aet.helios.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.cit.aet.helios.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.gitprovider.user.github.GitHubUserSyncService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class GitHubDataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncService.class);

    @Value("${monitoring.timeframe}")
    private int timeframe;

    @Value("${monitoring.runOnStartupCooldownInMinutes}")
    private int runOnStartupCooldownInMinutes;

    private final DataSyncStatusRepository dataSyncStatusRepository;
    private final GitHubUserSyncService userSyncService;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;

    public GitHubDataSyncService(
            DataSyncStatusRepository dataSyncStatusRepository, GitHubUserSyncService userSyncService,
            GitHubRepositorySyncService repositorySyncService,
            GitHubPullRequestSyncService pullRequestSyncService) {
        this.dataSyncStatusRepository = dataSyncStatusRepository;
        this.userSyncService = userSyncService;
        this.repositorySyncService = repositorySyncService;
        this.pullRequestSyncService = pullRequestSyncService;
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
            logger.info("Skipping sync, last sync was less than {} minutes ago", runOnStartupCooldownInMinutes);
            return;
        }

        // Start new sync
        var startTime = OffsetDateTime.now();

        var repositories = repositorySyncService.syncAllMonitoredRepositories();
        var pullRequests = pullRequestSyncService.syncPullRequestsOfAllRepositories(repositories, Optional.of(cutoffDate));
        userSyncService.syncAllExistingUsers();

        var endTime = OffsetDateTime.now();

        // Store successful sync status
        var syncStatus = new DataSyncStatus();
        syncStatus.setStartTime(startTime);
        syncStatus.setEndTime(endTime);
        dataSyncStatusRepository.save(syncStatus);
    }
}
