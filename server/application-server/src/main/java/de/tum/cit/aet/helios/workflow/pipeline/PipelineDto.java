package de.tum.cit.aet.helios.workflow.pipeline;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * The canonical pipeline for a branch or pull request: a fixed set of categories and nodes that are
 * always present. A node with no matching CI job yet reports {@code status = "PENDING"} and a null
 * conclusion, so the client renders it as not-started rather than omitting it.
 */
public record PipelineDto(List<Category> categories) {

  /** A titled group of pipeline nodes (e.g. "Build", "Tests"), rendered in declaration order. */
  public record Category(String name, List<Node> nodes) {}

  /**
   * @param status aggregated {@code WorkflowRun.Status} name (e.g. {@code PENDING}, {@code
   *     IN_PROGRESS}, {@code COMPLETED})
   * @param conclusion aggregated {@code WorkflowRun.Conclusion} name when completed, else null
   * @param htmlUrl a matching job's GitHub URL for linking, or null when pending
   */
  public record Node(
      String key,
      String label,
      String status,
      @Nullable String conclusion,
      @Nullable String htmlUrl) {}
}
