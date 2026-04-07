package de.tum.cit.aet.helios.workflow.logs.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class WorkflowRunLogStorageService {

  private final WorkflowRunRepository workflowRunRepository;
  private final GitHubService gitHubService;
  private final WorkflowRunLogStorageProperties properties;
  private final ObjectMapper objectMapper;
  private final WorkflowRunLogArchiveExtractor archiveExtractor;

  @Transactional
  public WorkflowRunLogCacheResult ensureLogsCached(Long workflowRunId) throws IOException {
    return ensureLogsCached(workflowRunId, false);
  }

  @Transactional
  public WorkflowRunLogCacheResult ensureLogsCached(Long workflowRunId, boolean forceRefresh)
      throws IOException {
    return cacheLogs(loadAccessibleCompletedRun(workflowRunId), forceRefresh);
  }

  private WorkflowRunLogCacheResult cacheLogs(WorkflowRun workflowRun, boolean forceRefresh)
      throws IOException {
    Long repositoryId = requireRepositoryId(workflowRun);
    Path runDirectory = getRunDirectory(workflowRun);
    Optional<WorkflowRunLogManifest> existingManifest = readManifest(runDirectory);
    if (existingManifest.isPresent()) {
      WorkflowRunLogManifest manifest = existingManifest.get();
      if (!forceRefresh && isCurrentCache(manifest, workflowRun)) {
        return new WorkflowRunLogCacheResult(workflowRun, runDirectory, manifest, true);
      }

      if (forceRefresh) {
        log.info(
            "Force refreshing workflow log cache repo={} run={} cachedRunAttempt={} "
                + "currentRunAttempt={}",
            repositoryId,
            workflowRun.getId(),
            manifest.runAttempt(),
            workflowRun.getRunAttempt());
      } else {
        log.info(
            "Refreshing stale workflow log cache repo={} run={} cachedRunAttempt={} "
                + "currentRunAttempt={}",
            repositoryId,
            workflowRun.getId(),
            manifest.runAttempt(),
            workflowRun.getRunAttempt());
      }
      deleteRecursively(runDirectory);
    }

    Files.createDirectories(runDirectory.getParent());
    Path tempDirectory =
        Files.createTempDirectory(
            runDirectory.getParent(), workflowRun.getId().toString() + "-workflow-logs-");

    try {
      int fileCount = downloadLogs(workflowRun, tempDirectory);
      if (fileCount == 0) {
        throw new IOException("GitHub returned no workflow logs for run " + workflowRun.getId());
      }

      WorkflowRunLogManifest manifest =
          new WorkflowRunLogManifest(
              workflowRun.getId(),
              repositoryId,
              currentTime(),
              fileCount,
              workflowRun.getRunAttempt());
      writeManifest(tempDirectory, manifest);
      promoteTempDirectory(tempDirectory, runDirectory);
      WorkflowRunLogManifest finalManifest = readManifest(runDirectory).orElse(manifest);
      return new WorkflowRunLogCacheResult(workflowRun, runDirectory, finalManifest, false);
    } catch (IOException | RuntimeException e) {
      deleteRecursivelyAndSuppressFailure(tempDirectory, e);
      throw e;
    }
  }

  private boolean isCurrentCache(WorkflowRunLogManifest manifest, WorkflowRun workflowRun) {
    return manifest.runAttempt() != null
        && manifest.runAttempt().equals(workflowRun.getRunAttempt());
  }

  private WorkflowRun loadAccessibleCompletedRun(Long workflowRunId) {
    WorkflowRun workflowRun = loadCompletedRun(workflowRunId);
    Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null || !repositoryId.equals(requireRepositoryId(workflowRun))) {
      throw new EntityNotFoundException("Workflow run not found with id: " + workflowRunId);
    }
    return workflowRun;
  }

  private WorkflowRun loadCompletedRun(Long workflowRunId) {
    WorkflowRun workflowRun =
        workflowRunRepository
            .findById(workflowRunId)
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "Workflow run not found with id: " + workflowRunId));
    if (workflowRun.getStatus() != WorkflowRun.Status.COMPLETED) {
      throw new IllegalStateException(
          "Workflow logs are only available after the workflow run has completed");
    }
    return workflowRun;
  }

  private Long requireRepositoryId(WorkflowRun workflowRun) {
    if (workflowRun.getRepository() == null
        || workflowRun.getRepository().getRepositoryId() == null) {
      throw new EntityNotFoundException(
          "Workflow run not found with id: " + workflowRun.getId());
    }

    return workflowRun.getRepository().getRepositoryId();
  }

  protected OffsetDateTime currentTime() {
    return OffsetDateTime.now();
  }

  private int downloadLogs(WorkflowRun workflowRun, Path tempDirectory) throws IOException {
    byte[] archive =
        gitHubService.downloadWorkflowRunLogs(
            workflowRun.getRepository().getNameWithOwner(), workflowRun.getId());
    return archiveExtractor.extractArchive(archive, tempDirectory);
  }

  private Path getRunDirectory(WorkflowRun workflowRun) {
    return properties
        .basePath()
        .resolve("repositories")
        .resolve(workflowRun.getRepository().getRepositoryId().toString())
        .resolve("workflow-runs")
        .resolve(workflowRun.getId().toString());
  }

  Optional<WorkflowRunLogManifest> readManifest(Path runDirectory) throws IOException {
    Path manifestPath = runDirectory.resolve(WorkflowRunLogManifest.FILE_NAME);
    if (!Files.isRegularFile(manifestPath)) {
      return Optional.empty();
    }
    try (InputStream inputStream = Files.newInputStream(manifestPath)) {
      return Optional.of(objectMapper.readValue(inputStream, WorkflowRunLogManifest.class));
    }
  }

  private void writeManifest(Path directory, WorkflowRunLogManifest manifest) throws IOException {
    Path manifestPath = directory.resolve(WorkflowRunLogManifest.FILE_NAME);
    objectMapper.writeValue(manifestPath.toFile(), manifest);
  }

  private void promoteTempDirectory(Path tempDirectory, Path runDirectory) throws IOException {
    Optional<WorkflowRunLogManifest> concurrentManifest = readManifest(runDirectory);
    if (concurrentManifest.isPresent()) {
      deleteRecursivelyBestEffort(tempDirectory);
      return;
    }

    if (Files.exists(runDirectory)) {
      deleteRecursively(runDirectory);
    }

    try {
      Files.move(tempDirectory, runDirectory, StandardCopyOption.ATOMIC_MOVE);
    } catch (FileAlreadyExistsException e) {
      deleteRecursivelyBestEffort(tempDirectory);
    } catch (IOException e) {
      try {
        Files.move(tempDirectory, runDirectory);
      } catch (IOException e2) {
        deleteRecursivelyAndSuppressFailure(tempDirectory, e2);
        throw e2;
      }
    }
  }

  void deleteRecursively(Path path) throws IOException {
    IOException deleteFailure = null;
    try (var walk = Files.walk(path)) {
      for (Path currentPath : walk.sorted(Comparator.reverseOrder()).toList()) {
        try {
          Files.deleteIfExists(currentPath);
        } catch (IOException e) {
          log.warn("Failed to delete workflow log path {}", currentPath, e);
          if (deleteFailure == null) {
            deleteFailure = new IOException("Failed to delete workflow log path " + currentPath);
          }
          deleteFailure.addSuppressed(e);
        }
      }
    } catch (NoSuchFileException ignored) {
      // path does not exist, nothing to delete
      return;
    }

    if (deleteFailure != null) {
      throw deleteFailure;
    }
  }

  private void deleteRecursivelyBestEffort(Path path) {
    try {
      deleteRecursively(path);
    } catch (IOException e) {
      log.warn("Failed to clean up workflow log path {}", path, e);
    }
  }

  private void deleteRecursivelyAndSuppressFailure(Path path, Throwable originalFailure) {
    try {
      deleteRecursively(path);
    } catch (IOException cleanupFailure) {
      originalFailure.addSuppressed(cleanupFailure);
      log.warn("Failed to clean up workflow log path {}", path, cleanupFailure);
    }
  }
}
