package de.tum.cit.aet.helios.github.sync;


import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Order(value = 2)
@Component
@Log4j2
@RequiredArgsConstructor
public class GitHubDataSyncScheduler {

  private final GitRepoRepository gitRepositoryRepository;
  private final GitHubDataSyncService dataSyncService;

  @Value("${monitoring.runOnStartup:true}")
  private boolean runOnStartup;

  @Value("${monitoring.repositories:}")
  private String[] repositoriesToMonitor;

  @EventListener(ApplicationReadyEvent.class)
  public void run() {
    if (runOnStartup) {
      log.info("Starting initial GitHub data sync...");
      syncData();
      log.info("Initial GitHub data sync completed.");
    }
  }

  @Scheduled(cron = "${monitoring.repository-sync-cron}")
  public void syncDataCron() {
    log.info("Starting scheduled GitHub data sync...");
    syncData();
    log.info("Scheduled GitHub data sync completed.");
  }

  public void syncData() {
    // Create a stream from the environment variables (assumed to be a String array)
    Stream<String> envRepoNames = Arrays.stream(repositoriesToMonitor);

    // Create a stream from the database repository names
    Stream<String> dbRepoNames = gitRepositoryRepository.findAll().stream()
        .map(GitRepository::getNameWithOwner);

    // Concatenate the two streams (environment variables first), apply distinct to remove duplicates,
    // and collect to a list (if you need the count) or process directly with forEach.
    List<String> uniqueRepoNames = Stream.concat(envRepoNames, dbRepoNames)
        .distinct()
        .toList();

    log.info("Found {} unique repositories to sync.", uniqueRepoNames.size());

    uniqueRepoNames.forEach(repoName -> {
      try {
        log.info("Scheduled sync for repository: {}", repoName);
        dataSyncService.syncRepositoryData(repoName);
        log.info("Scheduled sync completed for repository: {}", repoName);
      } catch (Exception ex) {
        log.error("Scheduled sync failed for repository: {}: {} {}",
            repoName, ex.getMessage(), ex);
      }
    });
  }
}
