package de.tum.cit.aet.helios.workflow.logs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowRunLogReaderService {

  private static final String MANIFEST_FILE_NAME = "_manifest.json";
  private static final String ROOT_GROUP_NAME = "Workflow run";

  private final WorkflowRunLogStorageService workflowRunLogStorageService;

  public WorkflowRunLogsResponse getLogs(Long workflowRunId) throws IOException {
    WorkflowRunLogCacheResult cacheResult = workflowRunLogStorageService.ensureLogsCached(workflowRunId);
    List<WorkflowRunLogGroupDto> groups = readGroups(cacheResult.runDirectory());

    return new WorkflowRunLogsResponse(
        cacheResult.workflowRun().getId(),
        cacheResult.workflowRun().getName(),
        cacheResult.workflowRun().getDisplayTitle(),
        cacheResult.workflowRun().getHtmlUrl(),
        cacheResult.cacheHit(),
        cacheResult.manifest().downloadedAt(),
        cacheResult.manifest().fileCount(),
        groups);
  }

  private List<WorkflowRunLogGroupDto> readGroups(Path runDirectory) throws IOException {
    List<Path> logFiles;
    try (var walk = Files.walk(runDirectory)) {
      logFiles =
          walk.filter(Files::isRegularFile)
              .filter(path -> !MANIFEST_FILE_NAME.equals(path.getFileName().toString()))
              .sorted(Comparator.comparing(path -> normalize(runDirectory.relativize(path))))
              .toList();
    }

    Map<String, List<WorkflowRunLogFileDto>> groupedFiles = new LinkedHashMap<>();
    for (Path logFile : logFiles) {
      Path relativePath = runDirectory.relativize(logFile);
      String groupName = resolveGroupName(relativePath);
      String filePath = normalize(relativePath);
      String displayName = resolveDisplayName(relativePath);
      String content = Files.readString(logFile, StandardCharsets.UTF_8);

      groupedFiles
          .computeIfAbsent(groupName, ignored -> new ArrayList<>())
          .add(new WorkflowRunLogFileDto(filePath, displayName, content));
    }

    return groupedFiles.entrySet().stream()
        .map(entry -> new WorkflowRunLogGroupDto(entry.getKey(), entry.getValue()))
        .toList();
  }

  private String resolveGroupName(Path relativePath) {
    if (relativePath.getNameCount() <= 1) {
      return ROOT_GROUP_NAME;
    }
    return relativePath.getName(0).toString();
  }

  private String resolveDisplayName(Path relativePath) {
    if (relativePath.getNameCount() <= 1) {
      return relativePath.getFileName().toString();
    }
    return normalize(relativePath.subpath(1, relativePath.getNameCount()));
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }
}
