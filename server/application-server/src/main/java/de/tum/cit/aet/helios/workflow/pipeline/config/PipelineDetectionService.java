package de.tum.cit.aet.helios.workflow.pipeline.config;

import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto.CategoryConfig;
import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto.NodeConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Turns observed CI job names into a suggested {@link PipelineConfigDto} with the canonical
 * Build → Test → Quality lanes. Two heuristics: a reusable-workflow prefix
 * {@code "<Stage> / <Job>"} (GitHub's shape for called workflows, e.g. Artemis' {@code "Build /"}),
 * and keyword classification of the job name. Matrix-leg suffixes like {@code " (PR, amd64)"} are
 * stripped so
 * legs collapse to one node whose matcher is the common prefix. Unclassifiable jobs go to "Other".
 * The Build/Test/Quality lanes are always present (possibly empty), giving every repo the default
 * structure.
 */
@Service
public class PipelineDetectionService {

  private static final Pattern TRAILING_PARENS = Pattern.compile("\\s*\\([^)]*\\)\\s*$");
  private static final String SEP = " / ";

  private static final Set<String> BUILD_KEYWORDS =
      Set.of("build", "compile", "package", "docker", "war", "assemble", "bundle", "image");
  private static final Set<String> TEST_KEYWORDS =
      Set.of("test", "spec", "junit", "vitest", "jest", "e2e", "cypress", "playwright");
  private static final Set<String> QUALITY_KEYWORDS =
      Set.of(
          "lint", "style", "checkstyle", "eslint", "prettier", "quality", "sonar", "codeql",
          "format", "analyze", "analysis");

  private static final String BUILD = "Build";
  private static final String TEST = "Test";
  private static final String QUALITY = "Quality";
  private static final String OTHER = "Other";

  /** Builds a suggested per-repo config from observed job names. */
  public PipelineConfigDto suggest(List<String> jobNames) {
    final Map<String, List<NodeConfig>> byCategory = new LinkedHashMap<>();
    byCategory.put(BUILD, new ArrayList<>());
    byCategory.put(TEST, new ArrayList<>());
    byCategory.put(QUALITY, new ArrayList<>());
    final List<NodeConfig> other = new ArrayList<>();
    final Set<String> seenKeys = new java.util.HashSet<>();

    for (String raw : jobNames) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      // Drop a trailing matrix parenthetical so "(amd64)" / "(arm64)" legs collapse to one node.
      final String name = TRAILING_PARENS.matcher(raw.trim()).replaceAll("");
      if (name.isEmpty()) {
        continue;
      }

      final int sep = name.indexOf(SEP);
      final String stage;
      final String label;
      if (sep > 0) {
        final String prefix = name.substring(0, sep).trim();
        label = name.substring(sep + SEP.length()).trim();
        final String byPrefix = classify(prefix);
        stage = byPrefix != null ? byPrefix : classify(name);
      } else {
        label = name;
        stage = classify(name);
      }

      final String key = slug(name);
      if (!seenKeys.add(key)) {
        continue;
      }
      // matcher = the (paren-stripped) full name, so it prefix-matches the job and its matrix legs.
      final NodeConfig node =
          new NodeConfig(key, label.isEmpty() ? name : label, List.of(name), null);
      byCategory.getOrDefault(stage, other).add(node);
    }

    final List<CategoryConfig> categories = new ArrayList<>();
    byCategory.forEach((cat, nodes) -> categories.add(new CategoryConfig(cat, nodes)));
    if (!other.isEmpty()) {
      categories.add(new CategoryConfig(OTHER, other));
    }
    return new PipelineConfigDto(categories);
  }

  private static String classify(String value) {
    final String lower = value.toLowerCase(Locale.ROOT);
    if (containsAny(lower, BUILD_KEYWORDS)) {
      return BUILD;
    }
    if (containsAny(lower, TEST_KEYWORDS)) {
      return TEST;
    }
    if (containsAny(lower, QUALITY_KEYWORDS)) {
      return QUALITY;
    }
    return null;
  }

  private static boolean containsAny(String haystack, Set<String> needles) {
    return needles.stream().anyMatch(haystack::contains);
  }

  private static String slug(String value) {
    final String s =
        value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    return s.isEmpty() ? "node" : s;
  }
}
