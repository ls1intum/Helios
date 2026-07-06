package de.tum.cit.aet.helios.workflow.pipeline;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Declares the canonical, always-visible pipeline nodes and how each maps to actual CI jobs.
 * Bound from {@code helios.pipeline.*} (see {@code application.yml}). The catalog is intentionally
 * curated and global: every configured node is rendered for every branch/PR, showing as
 * {@code PENDING} until a matching job appears — that is what makes Build/Tests/Quality visible
 * before CI starts.
 *
 * <p>A node matches a GitHub Actions job when the job name starts with any of
 * {@code jobNameMatchers} (case-insensitive) and, if set, the job's workflow name contains
 * {@code workflowNameMatcher}. Matching is prefix-based so reusable-workflow / matrix suffixes
 * (e.g. {@code "Build / Build and Push Docker Image (PR, amd64)"}) still match {@code "Build /
 * Build and Push Docker Image"}.
 */
@ConfigurationProperties(prefix = "helios.pipeline")
public record PipelineProperties(
    List<String> repositories, List<Category> categories, Node gate) {

  public PipelineProperties {
    // nameWithOwner allow-list: repositories that render the canonical catalog. Others fall back
    // to the group-based pipeline (PipelineService), so a non-matching repo isn't all-pending.
    repositories = repositories == null ? List.of() : repositories;
    categories = categories == null ? List.of() : categories;
  }

  /** A titled group of nodes (e.g. "Build", "Tests", "Quality"), rendered in declaration order. */
  public record Category(String name, List<Node> nodes) {
    public Category {
      nodes = nodes == null ? List.of() : nodes;
    }
  }

  /** A single pipeline node mapped to one or more CI jobs by name prefix. */
  public record Node(
      String key, String label, List<String> jobNameMatchers, String workflowNameMatcher) {
    public Node {
      jobNameMatchers = jobNameMatchers == null ? List.of() : jobNameMatchers;
    }
  }
}
