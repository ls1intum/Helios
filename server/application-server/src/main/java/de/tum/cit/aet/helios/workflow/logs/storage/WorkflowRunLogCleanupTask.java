package de.tum.cit.aet.helios.workflow.logs.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class WorkflowRunLogCleanupTask {

  private final WorkflowRunLogStorageProperties storageProperties;
  private final WorkflowRunLogCleanupProperties cleanupProperties;
  private final WorkflowRunLogStorageService workflowRunLogStorageService;

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    log.info(
        "Workflow-log cleanup is in {} mode. basePath={} cron={} maxAge={}",
        cleanupProperties.dryRun() ? "DRY-RUN" : "DELETE",
        storageProperties.basePath(),
        cleanupProperties.cron(),
        cleanupProperties.maxAge());
  }

  @Scheduled(cron = "${helios.logs.cleanup.cron:0 0 2 * * *}")
  public void purge() {
    Path repositoriesRoot = storageProperties.basePath().resolve("repositories");
    if (!Files.isDirectory(repositoriesRoot)) {
      log.info(
          "Workflow-log cleanup skipped because repositories root does not exist: {}",
          repositoriesRoot);
      return;
    }

    OffsetDateTime cutoff = currentTime().minus(cleanupProperties.maxAge());
    CleanupSummary summary = new CleanupSummary();
    log.info(
        "Workflow-log cleanup started. basePath={} cutoff={} dryRun={}",
        storageProperties.basePath(),
        cutoff,
        cleanupProperties.dryRun());

    try (Stream<Path> walk = Files.walk(repositoriesRoot, 3)) {
      walk.filter(Files::isDirectory)
          .filter(this::isFinalRunDirectory)
          .forEach(runDirectory -> cleanRunDirectory(runDirectory, cutoff, summary));
    } catch (IOException e) {
      log.warn("Workflow-log cleanup failed while scanning {}", repositoriesRoot, e);
      return;
    }

    log.info(
        "Workflow-log cleanup finished. scanned={} candidates={} deleted={} retained={} "
            + "missingManifest={} malformedManifest={} deleteFailures={}",
        summary.scanned,
        summary.candidates,
        summary.deleted,
        summary.retained,
        summary.missingManifest,
        summary.malformedManifest,
        summary.deleteFailures);
  }

  protected OffsetDateTime currentTime() {
    return OffsetDateTime.now();
  }

  private boolean isFinalRunDirectory(Path path) {
    Path parent = path.getParent();
    return parent != null
        && Objects.equals("workflow-runs", parent.getFileName().toString())
        && path.getFileName().toString().chars().allMatch(Character::isDigit);
  }

  private void cleanRunDirectory(
      Path runDirectory, OffsetDateTime cutoff, CleanupSummary summary) {
    summary.scanned++;

    WorkflowRunLogManifest manifest;
    try {
      var optManifest = workflowRunLogStorageService.readManifest(runDirectory);
      if (optManifest.isEmpty()) {
        summary.missingManifest++;
        log.warn("Skipping workflow log cache without manifest: {}", runDirectory);
        return;
      }
      manifest = optManifest.get();
    } catch (IOException e) {
      summary.malformedManifest++;
      log.warn("Skipping workflow log cache with unreadable manifest: {}", runDirectory, e);
      return;
    }

    if (manifest.downloadedAt() == null
        || manifest.workflowRunId() == null
        || manifest.repositoryId() == null) {
      summary.malformedManifest++;
      log.warn(
          "Skipping workflow log cache with incomplete manifest data: {}",
          runDirectory);
      return;
    }

    if (!manifest.downloadedAt().isBefore(cutoff)) {
      summary.retained++;
      return;
    }

    summary.candidates++;
    String action = cleanupProperties.dryRun() ? "DRY-RUN would delete" : "Deleting";
    log.info(
        "{} workflow log cache repo={} run={} downloadedAt={} path={}",
        action,
        manifest.repositoryId(),
        manifest.workflowRunId(),
        manifest.downloadedAt(),
        runDirectory);

    if (cleanupProperties.dryRun()) {
      return;
    }

    try {
      workflowRunLogStorageService.deleteRecursively(runDirectory);
      summary.deleted++;
    } catch (IOException e) {
      summary.deleteFailures++;
      log.warn(
          "Failed to delete expired workflow log cache repo={} run={} downloadedAt={} path={}",
          manifest.repositoryId(),
          manifest.workflowRunId(),
          manifest.downloadedAt(),
          runDirectory,
          e);
    }
  }

  private static final class CleanupSummary {
    private int scanned;
    private int candidates;
    private int deleted;
    private int retained;
    private int missingManifest;
    private int malformedManifest;
    private int deleteFailures;
  }
}
