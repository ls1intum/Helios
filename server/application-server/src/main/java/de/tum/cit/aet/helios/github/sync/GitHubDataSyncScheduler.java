package de.tum.cit.aet.helios.github.sync;


import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.List;
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
  private final GitHubFacade gitHubFacade;

  @Value("${monitoring.runOnStartup:true}")
  private boolean runOnStartup;

  @EventListener(ApplicationReadyEvent.class)
  public void run() {
    if (runOnStartup) {
      log.info("Starting initial GitHub data sync...");
      syncInstalledRepositories();
      log.info("Initial GitHub data sync completed.");
    }
  }

  @Scheduled(cron = "${monitoring.repository-sync-cron}")
  public void syncDataCron() {
    log.info("Starting scheduled GitHub data sync...");
    syncInstalledRepositories();
    log.info("Scheduled GitHub data sync completed.");
  }

  private void syncInstalledRepositories() {
    try {
      // Sync repositories that are installed the GitHub App
      List<String> syncRepositories = gitHubFacade.getInstalledRepositoriesForGitHubApp();
      log.info("Repositories will be synced: {}", syncRepositories);

      // Find the repositories that are not installed anymore
      List<String> repositoriesThatNeedsToBeDeleted = gitRepositoryRepository.findAll().stream()
          .map(GitRepository::getNameWithOwner)
          .filter(repo -> !syncRepositories.contains(repo))
          .toList();

      log.info("Repositories that needs to be deleted from db: {}",
          repositoriesThatNeedsToBeDeleted);

      // Sync the repositories that are installed
      syncRepositories.forEach(dataSyncService::syncRepository);
      // Delete the repositories that are not installed anymore
      repositoriesThatNeedsToBeDeleted.forEach(gitRepositoryRepository::deleteByNameWithOwner);
    } catch (Exception ex) {
      log.error("Failed to sync installed repositories: {} {}", ex.getMessage(), ex);
    }
  }
}
