package de.tum.cit.aet.helios.workflow.logs;

import de.tum.cit.aet.helios.deployment.DeploymentService;
import de.tum.cit.aet.helios.deployment.WorkflowJobDto;
import de.tum.cit.aet.helios.deployment.WorkflowStepDto;
import de.tum.cit.aet.helios.workflow.logs.storage.WorkflowRunLogCacheResult;
import de.tum.cit.aet.helios.workflow.logs.storage.WorkflowRunLogManifest;
import de.tum.cit.aet.helios.workflow.logs.storage.WorkflowRunLogStorageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class WorkflowRunLogReaderService {

  private final WorkflowRunLogStorageService workflowRunLogStorageService;
  private final DeploymentService deploymentService;
  private final WorkflowRunLogFileResolver resolver;

  public WorkflowRunLogsResponse getLogs(Long workflowRunId) throws IOException {
    return buildResponse(
        workflowRunLogStorageService.ensureLogsCached(workflowRunId), workflowRunId);
  }

  public WorkflowRunLogsResponse getLogs(Long workflowRunId, boolean forceRefresh)
      throws IOException {
    return buildResponse(
        workflowRunLogStorageService.ensureLogsCached(workflowRunId, forceRefresh),
        workflowRunId);
  }

  private WorkflowRunLogsResponse buildResponse(
      WorkflowRunLogCacheResult cacheResult, Long workflowRunId) throws IOException {
    List<WorkflowJobDto> jobs = loadWorkflowJobs(workflowRunId);
    List<WorkflowRunLogGroupDto> groups = readGroups(cacheResult.runDirectory(), jobs);

    return new WorkflowRunLogsResponse(
        cacheResult.workflowRun().getId(),
        cacheResult.workflowRun().getName(),
        cacheResult.workflowRun().getDisplayTitle(),
        cacheResult.workflowRun().getConclusion().orElse(null),
        cacheResult.workflowRun().getHtmlUrl(),
        cacheResult.cacheHit(),
        cacheResult.manifest().downloadedAt(),
        cacheResult.manifest().fileCount(),
        groups);
  }

  /**
   * Fetches job metadata from GitHub to enrich the log response (status, steps, timestamps).
   * This is best-effort: if GitHub is unavailable or the run has no job data, we still return
   * the raw log files without enrichment rather than failing the whole request.
   */
  private List<WorkflowJobDto> loadWorkflowJobs(Long workflowRunId) {
    try {
      return Optional.ofNullable(deploymentService.getWorkflowJobStatus(workflowRunId).getJobs())
          .orElseGet(List::of);
    } catch (RuntimeException ex) {
      log.warn(
          "Failed to enrich workflow logs with GitHub job status for run {}",
          workflowRunId,
          ex);
      return List.of();
    }
  }

  private List<WorkflowRunLogGroupDto> readGroups(Path runDirectory, List<WorkflowJobDto> jobs)
      throws IOException {
    List<Path> logFiles = listLogFiles(runDirectory);
    Map<String, List<RawWorkflowRunLogFile>> groupedFiles =
        groupFilesByName(logFiles, runDirectory);
    Map<Long, Integer> jobOrderById = buildJobOrderById(jobs);
    List<WorkflowRunLogGroupView> groupViews = buildGroupViews(groupedFiles, jobs, jobOrderById);

    // Sort priority:
    // 1. Groups matched to a GitHub job come first (unmatched groups are appended at the end).
    // 2. Among matched groups, sort by job start time (earliest first).
    // 3. Break ties using the job's position in the GitHub API response.
    // 4. Final tiebreaker: order in which files appeared in the downloaded archive.
    return groupViews.stream()
        .sorted(
            Comparator.comparing(WorkflowRunLogGroupView::hasMatchedJob).reversed()
                .thenComparing(
                    WorkflowRunLogGroupView::jobStartedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(WorkflowRunLogGroupView::jobOrder)
                .thenComparingInt(WorkflowRunLogGroupView::sourceOrder))
        .map(WorkflowRunLogGroupView::group)
        .toList();
  }

  private List<Path> listLogFiles(Path runDirectory) throws IOException {
    try (var walk = Files.walk(runDirectory)) {
      return walk.filter(Files::isRegularFile)
          .filter(
              path ->
                  !WorkflowRunLogManifest.FILE_NAME.equals(
                      path.getFileName().toString()))
          .sorted(Comparator.comparing(path -> resolver.normalize(runDirectory.relativize(path))))
          .toList();
    }
  }

  /**
   * Groups log files by their resolved group name (typically the job name derived from the
   * directory structure). A LinkedHashMap is used so that insertion order — which reflects the
   * sorted file traversal — is preserved as the {@code sourceOrder} tiebreaker during sorting.
   */
  private Map<String, List<RawWorkflowRunLogFile>> groupFilesByName(
      List<Path> logFiles, Path runDirectory) throws IOException {
    Map<String, List<RawWorkflowRunLogFile>> groupedFiles = new LinkedHashMap<>();
    for (Path logFile : logFiles) {
      Path relativePath = runDirectory.relativize(logFile);
      String groupName = resolver.resolveGroupName(relativePath);
      String filePath = resolver.normalize(relativePath);
      String displayName = resolver.resolveDisplayName(relativePath, groupName);
      String content = resolver.normalizeContent(Files.readString(logFile, StandardCharsets.UTF_8));
      groupedFiles
          .computeIfAbsent(groupName, ignored -> new ArrayList<>())
          .add(new RawWorkflowRunLogFile(filePath, displayName, content));
    }
    return groupedFiles;
  }

  /**
   * Records the position of each job in the GitHub API response so that groups without a
   * {@code startedAt} timestamp can still be ordered consistently with the GitHub UI.
   */
  private Map<Long, Integer> buildJobOrderById(List<WorkflowJobDto> jobs) {
    Map<Long, Integer> jobOrderById = new LinkedHashMap<>();
    for (int index = 0; index < jobs.size(); index++) {
      WorkflowJobDto job = jobs.get(index);
      if (job.getId() != null) {
        jobOrderById.put(job.getId(), index);
      }
    }
    return jobOrderById;
  }

  private List<WorkflowRunLogGroupView> buildGroupViews(
      Map<String, List<RawWorkflowRunLogFile>> groupedFiles,
      List<WorkflowJobDto> jobs,
      Map<Long, Integer> jobOrderById) {
    List<WorkflowRunLogGroupView> groupViews = new ArrayList<>();
    int sourceOrder = 0;
    for (Map.Entry<String, List<RawWorkflowRunLogFile>> entry : groupedFiles.entrySet()) {
      groupViews.add(buildGroupView(entry, jobs, jobOrderById, sourceOrder++));
    }
    return groupViews;
  }

  private WorkflowRunLogGroupView buildGroupView(
      Map.Entry<String, List<RawWorkflowRunLogFile>> entry,
      List<WorkflowJobDto> jobs,
      Map<Long, Integer> jobOrderById,
      int sourceOrder) {
    Optional<WorkflowJobDto> optJob = findJobForGroup(entry.getKey(), jobs);
    WorkflowJobDto job = optJob.orElse(null);
    List<WorkflowRunLogStepDto> steps = mapSteps(job);
    // Pre-compute sort keys so that resolveBaseDisplayName is not called O(n log n) times
    // inside the comparator during sorting.
    Map<String, Integer> fileOrders =
        entry.getValue().stream()
            .collect(
                Collectors.toMap(
                    RawWorkflowRunLogFile::path,
                    file -> {
                      Path p = Path.of(file.path());
                      return resolver.resolveFileOrder(
                          p, entry.getKey(), resolver.resolveBaseDisplayName(p));
                    }));
    WorkflowRunLogGroupDto group =
        new WorkflowRunLogGroupDto(
            entry.getKey(),
            optJob.map(WorkflowJobDto::getName).orElse(null),
            optJob.map(WorkflowJobDto::getStatus).orElse(null),
            optJob.map(WorkflowJobDto::getConclusion).orElse(null),
            steps,
            entry.getValue().stream()
                .sorted(
                    Comparator.comparingInt(
                            (RawWorkflowRunLogFile file) -> fileOrders.get(file.path()))
                        .thenComparing(RawWorkflowRunLogFile::displayName))
                .map(file -> enrichFile(file, entry.getKey(), job))
                .toList());

    int jobOrder =
        optJob
            .filter(j -> j.getId() != null)
            .map(j -> jobOrderById.getOrDefault(j.getId(), Integer.MAX_VALUE))
            .orElse(Integer.MAX_VALUE);
    return new WorkflowRunLogGroupView(
        group, optJob.map(WorkflowJobDto::getStartedAt).orElse(null), jobOrder, sourceOrder);
  }

  private WorkflowRunLogFileDto enrichFile(
      RawWorkflowRunLogFile file, String groupName, WorkflowJobDto job) {
    Optional<WorkflowStepDto> step =
        Optional.ofNullable(findStepForFile(Path.of(file.path()), groupName, job));
    return new WorkflowRunLogFileDto(
        file.path(),
        file.displayName(),
        step.map(WorkflowStepDto::getNumber).orElse(null),
        step.map(WorkflowStepDto::getName).orElse(null),
        step.map(WorkflowStepDto::getStatus).orElse(null),
        step.map(WorkflowStepDto::getConclusion).orElse(null),
        step.map(WorkflowStepDto::getStartedAt).orElse(null),
        step.map(WorkflowStepDto::getCompletedAt).orElse(null),
        file.content());
  }

  private List<WorkflowRunLogStepDto> mapSteps(WorkflowJobDto job) {
    if (job == null || job.getSteps() == null) {
      return List.of();
    }

    return job.getSteps().stream()
        .map(
            step ->
                new WorkflowRunLogStepDto(
                    step.getNumber(),
                    step.getName(),
                    step.getStatus(),
                    step.getConclusion(),
                    step.getStartedAt(),
                    step.getCompletedAt()))
        .toList();
  }

  /**
   * Matches a log group to a GitHub job by normalized name. Requires an exact 1-to-1 match:
   * if zero or more than one job matches, no enrichment is applied to avoid mis-attribution.
   */
  private Optional<WorkflowJobDto> findJobForGroup(String groupName, List<WorkflowJobDto> jobs) {
    String normalizedGroupName = resolver.normalizeComparisonKey(groupName);
    if (normalizedGroupName.isBlank()) {
      return Optional.empty();
    }

    List<WorkflowJobDto> matchingJobs =
        jobs.stream()
            .filter(
                job ->
                    resolver.normalizeComparisonKey(job.getName())
                        .equals(normalizedGroupName))
            .toList();
    return matchingJobs.size() == 1 ? Optional.of(matchingJobs.getFirst()) : Optional.empty();
  }

  private WorkflowStepDto findStepForFile(Path relativePath, String groupName, WorkflowJobDto job) {
    if (job == null || job.getSteps() == null || job.getSteps().isEmpty()) {
      return null;
    }

    String baseDisplayName = resolver.resolveBaseDisplayName(relativePath);
    // Aggregate log files (e.g. a single file that covers the whole job) are not tied to
    // an individual step, so skip step matching for them.
    if (resolver.isAggregateLogFile(relativePath, groupName, baseDisplayName)) {
      return null;
    }

    Integer stepNumber = resolver.extractFileNumberPrefix(relativePath.getFileName().toString());
    if (stepNumber == null || stepNumber <= 0) {
      return null;
    }

    return job.getSteps().stream()
        .filter(step -> Objects.equals(step.getNumber(), stepNumber))
        .findFirst()
        .orElse(null);
  }

  /** Intermediate representation of a log file before it is enriched with GitHub step metadata. */
  private record RawWorkflowRunLogFile(String path, String displayName, String content) {}

  /**
   * Wraps a log group with the sort keys needed to order groups before the final DTO is produced.
   * {@code hasMatchedJob} puts matched groups first; {@code jobStartedAt} and {@code jobOrder}
   * order matched groups chronologically; {@code sourceOrder} breaks remaining ties by archive
   * insertion order.
   */
  private record WorkflowRunLogGroupView(
      WorkflowRunLogGroupDto group,
      java.time.OffsetDateTime jobStartedAt,
      int jobOrder,
      int sourceOrder) {

    private boolean hasMatchedJob() {
      return group.jobName() != null;
    }
  }
}
