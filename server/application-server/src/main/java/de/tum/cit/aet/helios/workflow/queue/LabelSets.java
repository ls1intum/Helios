package de.tum.cit.aet.helios.workflow.queue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Helpers for canonicalizing and hashing GitHub Actions label sets. */
public final class LabelSets {

  private static final Set<String> GITHUB_HOSTED_PREFIXES =
      Set.of(
          "ubuntu-",
          "windows-",
          "macos-",
          "macOS-",
          "mac-",
          "buildjet-",
          "namespace-default");

  private static final Set<String> EXACT_GITHUB_HOSTED =
      Set.of("ubuntu-latest", "windows-latest", "macos-latest");

  private LabelSets() {}

  /** Returns labels lower-cased and sorted; null/empty → empty list. */
  public static List<String> canonical(List<String> labels) {
    if (labels == null || labels.isEmpty()) {
      return List.of();
    }
    List<String> normalized = new ArrayList<>(labels.size());
    for (String label : labels) {
      if (label != null && !label.isBlank()) {
        normalized.add(label.toLowerCase(Locale.ROOT));
      }
    }
    Collections.sort(normalized);
    return normalized;
  }

  /** SHA-1 (40-char hex) of the canonical join. Stable for equal label sets. */
  public static String hash(List<String> labels) {
    List<String> canonical = canonical(labels);
    String joined = String.join("", canonical);
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = md.digest(joined.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(40);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 unavailable", e);
    }
  }

  /** Derives the runner kind from the label set. */
  public static WorkflowJob.RunnerKind deriveRunnerKind(List<String> labels) {
    List<String> canonical = canonical(labels);
    if (canonical.isEmpty()) {
      return WorkflowJob.RunnerKind.UNKNOWN;
    }
    if (canonical.contains("self-hosted")) {
      return WorkflowJob.RunnerKind.SELF_HOSTED;
    }
    for (String label : canonical) {
      if (EXACT_GITHUB_HOSTED.contains(label)) {
        return WorkflowJob.RunnerKind.GITHUB_HOSTED;
      }
      for (String prefix : GITHUB_HOSTED_PREFIXES) {
        if (label.startsWith(prefix.toLowerCase(Locale.ROOT))) {
          return WorkflowJob.RunnerKind.GITHUB_HOSTED;
        }
      }
    }
    return WorkflowJob.RunnerKind.UNKNOWN;
  }
}
