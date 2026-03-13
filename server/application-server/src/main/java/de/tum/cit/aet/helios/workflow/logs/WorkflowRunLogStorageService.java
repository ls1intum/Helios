package de.tum.cit.aet.helios.workflow.logs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import de.tum.cit.aet.helios.deployment.WorkflowJobDto;
import de.tum.cit.aet.helios.deployment.WorkflowJobsResponse;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class WorkflowRunLogStorageService {

  private static final String MANIFEST_FILE_NAME = "_manifest.json";
  private static final Duration WORKFLOW_ARCHIVE_RETENTION_WINDOW = Duration.ofHours(1);
  private static final Pattern UNSAFE_FILE_SEGMENT_PATTERN =
      Pattern.compile("[^a-zA-Z0-9 _-]+");
  private static final String JOB_LOG_FILE_SUFFIX = ".txt";

  private final WorkflowRunRepository workflowRunRepository;
  private final GitHubService gitHubService;
  private final WorkflowRunLogStorageProperties properties;
  private final ObjectMapper objectMapper;

  @Transactional
  public WorkflowRunLogCacheResult ensureLogsCached(Long workflowRunId) throws IOException {
    return cacheLogs(loadAccessibleCompletedRun(workflowRunId));
  }

  private WorkflowRunLogCacheResult cacheLogs(WorkflowRun workflowRun) throws IOException {
    Path runDirectory = getRunDirectory(workflowRun);
    Optional<WorkflowRunLogManifest> existingManifest = readManifest(runDirectory);
    if (existingManifest.isPresent()) {
      return new WorkflowRunLogCacheResult(workflowRun, runDirectory, existingManifest.get(), true);
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
              workflowRun.getId(), requireRepositoryId(workflowRun), currentTime(), fileCount);
      writeManifest(tempDirectory, manifest);
      promoteTempDirectory(tempDirectory, runDirectory);
      WorkflowRunLogManifest finalManifest = readManifest(runDirectory).orElse(manifest);
      return new WorkflowRunLogCacheResult(workflowRun, runDirectory, finalManifest, false);
    } catch (IOException e) {
      deleteRecursively(tempDirectory);
      throw e;
    } catch (RuntimeException e) {
      deleteRecursively(tempDirectory);
      throw e;
    }
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
    LogDownloadPlan downloadPlan = resolveDownloadPlan(workflowRun);
    if (downloadPlan.source() == WorkflowLogSource.WORKFLOW_ARCHIVE) {
      byte[] archive =
          gitHubService.downloadWorkflowRunLogs(
              workflowRun.getRepository().getNameWithOwner(), workflowRun.getId());
      return extractArchive(archive, tempDirectory);
    }

    return writeJobLogs(tempDirectory, workflowRun, downloadPlan.jobs());
  }

  private LogDownloadPlan resolveDownloadPlan(WorkflowRun workflowRun) throws IOException {
    OffsetDateTime completionTime = workflowRun.getUpdatedAt();
    WorkflowJobsResponse workflowJobs = null;

    if (completionTime == null) {
      workflowJobs = loadWorkflowJobs(workflowRun);
      completionTime = resolveCompletionTime(workflowJobs);
    }

    if (completionTime == null) {
      throw new IOException(
          "Unable to determine completion time for workflow run " + workflowRun.getId());
    }

    Duration age = Duration.between(completionTime, currentTime());
    if (age.compareTo(WORKFLOW_ARCHIVE_RETENTION_WINDOW) <= 0) {
      return new LogDownloadPlan(WorkflowLogSource.WORKFLOW_ARCHIVE, List.of());
    }

    if (workflowJobs == null) {
      workflowJobs = loadWorkflowJobs(workflowRun);
    }
    return new LogDownloadPlan(
        WorkflowLogSource.JOB_LOGS, requireJobs(workflowRun, workflowJobs));
  }

  private WorkflowJobsResponse loadWorkflowJobs(WorkflowRun workflowRun) throws IOException {
    String rawJsonResponse =
        gitHubService.getWorkflowJobStatus(
            workflowRun.getRepository().getNameWithOwner(), workflowRun.getId());

    return objectMapper
        .copy()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .configure(
            com.fasterxml.jackson.databind.DeserializationFeature
                .FAIL_ON_UNKNOWN_PROPERTIES,
            false)
        .readerFor(WorkflowJobsResponse.class)
        .readValue(rawJsonResponse);
  }

  private OffsetDateTime resolveCompletionTime(WorkflowJobsResponse workflowJobs) {
    if (workflowJobs == null || workflowJobs.getJobs() == null) {
      return null;
    }

    return workflowJobs.getJobs().stream()
        .map(WorkflowJobDto::getCompletedAt)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  private List<WorkflowJobDto> requireJobs(
      WorkflowRun workflowRun, WorkflowJobsResponse workflowJobs)
      throws IOException {
    if (workflowJobs == null
        || workflowJobs.getJobs() == null
        || workflowJobs.getJobs().isEmpty()) {
      throw new IOException(
          "GitHub returned no workflow jobs for workflow run " + workflowRun.getId());
    }
    return workflowJobs.getJobs();
  }

  private int writeJobLogs(Path tempDirectory, WorkflowRun workflowRun, List<WorkflowJobDto> jobs)
      throws IOException {
    int fileCount = 0;
    Map<String, Integer> usedJobFileNames = new LinkedHashMap<>();

    for (int index = 0; index < jobs.size(); index++) {
      WorkflowJobDto job = jobs.get(index);
      if (job == null || job.getId() == null) {
        continue;
      }

      byte[] logContent =
          gitHubService.downloadWorkflowJobLogs(
              workflowRun.getRepository().getNameWithOwner(), job.getId());
      Path targetPath =
          tempDirectory.resolve(
              buildJobLogFileName(index, resolveUniqueJobFileName(job, index, usedJobFileNames)));
      Files.createDirectories(targetPath.getParent());
      Files.write(targetPath, logContent);
      fileCount++;
    }

    return fileCount;
  }

  private String resolveUniqueJobFileName(
      WorkflowJobDto job, int index, Map<String, Integer> usedJobFileNames) {
    String baseName =
        sanitizeFileSegment(Optional.ofNullable(job.getName()).orElse("job-" + (index + 1)));
    Integer currentCount = usedJobFileNames.get(baseName);
    if (currentCount == null) {
      usedJobFileNames.put(baseName, 1);
      return baseName;
    }

    int nextCount = currentCount + 1;
    usedJobFileNames.put(baseName, nextCount);
    return baseName + "_" + nextCount;
  }

  private String sanitizeFileSegment(String value) {
    String sanitized = UNSAFE_FILE_SEGMENT_PATTERN.matcher(value).replaceAll("_").trim();
    if (sanitized.isBlank()) {
      return "job";
    }
    return sanitized;
  }

  private String buildJobLogFileName(int index, String baseName) {
    return index + "_" + baseName + JOB_LOG_FILE_SUFFIX;
  }

  private Path getRunDirectory(WorkflowRun workflowRun) {
    return properties
        .basePath()
        .resolve("repositories")
        .resolve(workflowRun.getRepository().getRepositoryId().toString())
        .resolve("workflow-runs")
        .resolve(workflowRun.getId().toString());
  }

  private Optional<WorkflowRunLogManifest> readManifest(Path runDirectory) throws IOException {
    Path manifestPath = runDirectory.resolve(MANIFEST_FILE_NAME);
    if (!Files.isRegularFile(manifestPath)) {
      return Optional.empty();
    }
    try (InputStream inputStream = Files.newInputStream(manifestPath)) {
      return Optional.of(objectMapper.readValue(inputStream, WorkflowRunLogManifest.class));
    }
  }

  private void writeManifest(Path directory, WorkflowRunLogManifest manifest) throws IOException {
    Path manifestPath = directory.resolve(MANIFEST_FILE_NAME);
    objectMapper.writeValue(manifestPath.toFile(), manifest);
  }

  private int extractArchive(byte[] archive, Path tempDirectory) throws IOException {
    int fileCount = 0;

    try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zipInput.getNextEntry()) != null) {
        Path targetPath = resolveZipEntry(tempDirectory, entry);
        if (entry.isDirectory()) {
          Files.createDirectories(targetPath);
        } else {
          Files.createDirectories(targetPath.getParent());
          Files.copy(zipInput, targetPath, StandardCopyOption.REPLACE_EXISTING);
          fileCount++;
        }
        zipInput.closeEntry();
      }
    }

    return fileCount;
  }

  private Path resolveZipEntry(Path tempDirectory, ZipEntry entry) throws IOException {
    Path resolvedPath = tempDirectory.resolve(entry.getName()).normalize();
    if (!resolvedPath.startsWith(tempDirectory)) {
      throw new IOException("Refusing to extract unsafe log entry: " + entry.getName());
    }
    return resolvedPath;
  }

  private void promoteTempDirectory(Path tempDirectory, Path runDirectory) throws IOException {
    Optional<WorkflowRunLogManifest> concurrentManifest = readManifest(runDirectory);
    if (concurrentManifest.isPresent()) {
      deleteRecursively(tempDirectory);
      return;
    }

    if (Files.exists(runDirectory)) {
      deleteRecursively(runDirectory);
    }

    try {
      Files.move(tempDirectory, runDirectory, StandardCopyOption.ATOMIC_MOVE);
    } catch (FileAlreadyExistsException e) {
      deleteRecursively(tempDirectory);
    } catch (IOException e) {
      Files.move(tempDirectory, runDirectory);
    }
  }

  private void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }

    try (var walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
    }
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("Failed to delete temporary workflow log path {}", path, e);
    }
  }

  private enum WorkflowLogSource {
    WORKFLOW_ARCHIVE,
    JOB_LOGS
  }

  private record LogDownloadPlan(WorkflowLogSource source, List<WorkflowJobDto> jobs) {}
}
