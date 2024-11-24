package de.tum.cit.aet.helios.github.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.log4j.Log4j2;

@Order(value = 2)
@Component
@Log4j2
public class GitHubDataSyncScheduler {
    private final GitHubDataSyncService dataSyncService;

    @Value("${monitoring.runOnStartup:true}")
    private boolean runOnStartup;

    public GitHubDataSyncScheduler(GitHubDataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        if (runOnStartup) {
            log.info("Starting initial GitHub data sync...");
            dataSyncService.syncData();
            log.info("Initial GitHub data sync completed.");
        }
    }

    @Scheduled(cron = "${monitoring.repository-sync-cron}")
    public void syncDataCron() {
        log.info("Starting scheduled GitHub data sync...");
        dataSyncService.syncData();
        log.info("Scheduled GitHub data sync completed.");
    }
}