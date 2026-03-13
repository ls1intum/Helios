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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowRunLogReaderService {

  private static final String MANIFEST_FILE_NAME = "_manifest.json";
  private static final String ROOT_GROUP_NAME = "Workflow run";
  private static final Pattern ROOT_JOB_LOG_FILE_PATTERN =
      Pattern.compile("^\\d+_([^.]+)\\.txt$");
  private static final Pattern FILE_NUMBER_PREFIX_PATTERN =
      Pattern.compile("^(\\d+)_+(.+)$");
  private static final Pattern TXT_SUFFIX_PATTERN = Pattern.compile("\\.txt$");
  private static final Pattern TIMESTAMP_PREFIX_PATTERN =
      Pattern.compile(
          "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})\\s+");
  private static final Pattern ANSI_ESCAPE_PATTERN =
      Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
  private static final String GITHUB_MARKER_PREFIX = "##[";
  private static final String GROUP_START_MARKER = "##[group]";
  private static final String GROUP_END_MARKER = "##[endgroup]";

  private final WorkflowRunLogStorageService workflowRunLogStorageService;

  public WorkflowRunLogsResponse getLogs(Long workflowRunId) throws IOException {
    WorkflowRunLogCacheResult cacheResult =
        workflowRunLogStorageService.ensureLogsCached(workflowRunId);
    List<WorkflowRunLogGroupDto> groups = readGroups(cacheResult.runDirectory());

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
      String content = normalizeContent(Files.readString(logFile, StandardCharsets.UTF_8));

      groupedFiles
          .computeIfAbsent(groupName, ignored -> new ArrayList<>())
          .add(new WorkflowRunLogFileDto(filePath, displayName, content));
    }

    return groupedFiles.entrySet().stream()
        .map(
            entry ->
                new WorkflowRunLogGroupDto(
                    entry.getKey(),
                    entry.getValue().stream()
                        .sorted(
                            Comparator.comparingInt(
                                    (WorkflowRunLogFileDto file) -> resolveFileOrder(file.path()))
                                .thenComparing(WorkflowRunLogFileDto::displayName))
                        .toList()))
        .toList();
  }

  private String resolveGroupName(Path relativePath) {
    if (relativePath.getNameCount() <= 1) {
      String rootJobGroupName = resolveRootJobGroupName(relativePath.getFileName().toString());
      if (rootJobGroupName != null) {
        return rootJobGroupName;
      }
      return ROOT_GROUP_NAME;
    }
    return relativePath.getName(0).toString();
  }

  private String resolveDisplayName(Path relativePath) {
    if (relativePath.getNameCount() <= 1) {
      return stripTxtSuffix(stripFileNumberPrefix(relativePath.getFileName().toString()));
    }
    return stripTxtSuffix(
        stripFileNumberPrefix(
            normalize(relativePath.subpath(1, relativePath.getNameCount()))));
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }

  private String resolveRootJobGroupName(String fileName) {
    Matcher matcher = ROOT_JOB_LOG_FILE_PATTERN.matcher(fileName);
    if (!matcher.matches()) {
      return null;
    }

    return matcher.group(1);
  }

  private int resolveFileOrder(String filePath) {
    String fileName = Path.of(filePath).getFileName().toString();
    Matcher matcher = FILE_NUMBER_PREFIX_PATTERN.matcher(fileName);
    if (!matcher.matches()) {
      return Integer.MAX_VALUE;
    }

    return Integer.parseInt(matcher.group(1));
  }

  private String stripFileNumberPrefix(String value) {
    Matcher matcher = FILE_NUMBER_PREFIX_PATTERN.matcher(value);
    if (!matcher.matches()) {
      return value;
    }

    return matcher.group(2);
  }

  private String stripTxtSuffix(String value) {
    return TXT_SUFFIX_PATTERN.matcher(value).replaceFirst("");
  }

  private String normalizeContent(String content) {
    return content.lines()
        .map(this::normalizeLine)
        .filter(Objects::nonNull)
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private String normalizeLine(String line) {
    String normalizedLine = TIMESTAMP_PREFIX_PATTERN.matcher(line).replaceFirst("");
    normalizedLine = ANSI_ESCAPE_PATTERN.matcher(normalizedLine).replaceAll("");

    if (normalizedLine.startsWith(GROUP_START_MARKER)) {
      return "[group]" + normalizedLine.substring(GROUP_START_MARKER.length());
    }

    if (normalizedLine.startsWith(GROUP_END_MARKER)) {
      return "[endgroup]";
    }

    if (normalizedLine.startsWith(GITHUB_MARKER_PREFIX)) {
      return normalizedLine.substring(2);
    }

    return normalizedLine;
  }
}
