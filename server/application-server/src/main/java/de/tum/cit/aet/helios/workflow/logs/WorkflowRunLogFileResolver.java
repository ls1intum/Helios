package de.tum.cit.aet.helios.workflow.logs;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorkflowRunLogFileResolver {

  static final String ROOT_GROUP_NAME = "Workflow run";
  static final String FULL_JOB_LOG_DISPLAY_NAME = "Full job log";

  // Matches root-level combined job logs, e.g. "0_deploy.txt" → group(1) = "deploy"
  private static final Pattern ROOT_JOB_LOG_FILE_PATTERN =
      Pattern.compile("^\\d+_([^.]+)\\.txt$");
  // Matches files/paths that start with a step number, e.g. "3__checkout.txt" or "10_Run tests.txt"
  private static final Pattern FILE_NUMBER_PREFIX_PATTERN =
      Pattern.compile("^(\\d+)_+(.+)$");
  private static final Pattern TXT_SUFFIX_PATTERN = Pattern.compile("\\.txt$");
  // Matches the ISO-8601 timestamp GitHub prepends to every log line,
  // e.g. "2026-03-12T20:46:28.973Z "
  private static final Pattern TIMESTAMP_PREFIX_PATTERN =
      Pattern.compile(
          "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})\\s+");
  private static final Pattern ANSI_ESCAPE_PATTERN =
      Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
  private static final String GITHUB_MARKER_PREFIX = "##[";
  private static final String GROUP_START_MARKER = "##[group]";
  private static final String GROUP_END_MARKER = "##[endgroup]";

  // Subdirectory files are grouped by their top-level directory name.
  // Root-level files match the "N_jobname.txt" pattern if they are combined job logs;
  // everything else falls into the catch-all "Workflow run" group.
  String resolveGroupName(Path relativePath) {
    if (relativePath.getNameCount() > 1) {
      return relativePath.getName(0).toString();
    }
    Matcher m = ROOT_JOB_LOG_FILE_PATTERN.matcher(relativePath.getFileName().toString());
    return m.matches() ? m.group(1) : ROOT_GROUP_NAME;
  }

  String resolveDisplayName(Path relativePath, String groupName) {
    String base = resolveBaseDisplayName(relativePath);
    return isAggregateLogFile(relativePath, groupName, base) ? FULL_JOB_LOG_DISPLAY_NAME : base;
  }

  // Strips the step-number prefix and .txt suffix from the filename (or path-below-group) to get
  // a human-readable display name, e.g. "build/3__Run tests.txt" → "Run tests".
  String resolveBaseDisplayName(Path relativePath) {
    String raw =
        relativePath.getNameCount() <= 1
            ? relativePath.getFileName().toString()
            : normalize(relativePath.subpath(1, relativePath.getNameCount()));
    return stripTxtSuffix(stripFileNumberPrefix(raw));
  }

  String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }

  // Numbered step files sort by step number; unnumbered non-aggregate files sort second-to-last;
  // the aggregate "Full job log" always sorts last.
  int resolveFileOrder(Path relativePath, String groupName, String displayName) {
    if (isAggregateLogFile(relativePath, groupName, displayName)) {
      return Integer.MAX_VALUE;
    }
    Integer stepNumber = extractFileNumberPrefix(relativePath.getFileName().toString());
    return (stepNumber != null && stepNumber > 0) ? stepNumber : Integer.MAX_VALUE - 1;
  }

  // A file is the aggregate job log in two cases:
  // 1. Root-level "N_jobname.txt" whose embedded job name matches the group name.
  // 2. A nested file with no step-number prefix whose display name matches the group name.
  boolean isAggregateLogFile(Path relativePath, String groupName, String displayName) {
    String fileName = relativePath.getFileName().toString();
    Matcher rootJobMatcher = ROOT_JOB_LOG_FILE_PATTERN.matcher(fileName);
    if (relativePath.getNameCount() <= 1 && rootJobMatcher.matches()) {
      return normalizeComparisonKey(rootJobMatcher.group(1))
          .equals(normalizeComparisonKey(groupName));
    }
    return extractFileNumberPrefix(fileName) == null
        && normalizeComparisonKey(displayName).equals(normalizeComparisonKey(groupName));
  }

  Integer extractFileNumberPrefix(String fileName) {
    Matcher m = FILE_NUMBER_PREFIX_PATTERN.matcher(fileName);
    return m.matches() ? Integer.parseInt(m.group(1)) : null;
  }

  private String stripFileNumberPrefix(String value) {
    Matcher m = FILE_NUMBER_PREFIX_PATTERN.matcher(value);
    return m.matches() ? m.group(2) : value;
  }

  private String stripTxtSuffix(String value) {
    return TXT_SUFFIX_PATTERN.matcher(value).replaceFirst("");
  }

  // Reduces a name to lowercase alphanumeric tokens so that "My Job" and "my-job" compare equal.
  // Used to fuzzy-match archive directory names against GitHub API job/step names.
  String normalizeComparisonKey(String value) {
    return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
  }

  String normalizeContent(String content) {
    return content.lines().map(this::normalizeLine).collect(Collectors.joining("\n"));
  }

  // Strips the GitHub Actions timestamp and ANSI codes from each line, then rewrites
  // "##[type]..." markers to "[type]..." (dropping the leading "##" but preserving the brackets).
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
