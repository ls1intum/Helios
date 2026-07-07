package de.tum.cit.aet.helios.workflow.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * The canonical pipeline for a branch or pull request: a fixed set of categories and nodes that are
 * always present. A node with no matching CI job yet reports {@code status = "PENDING"} and a null
 * conclusion, so the client renders it as not-started rather than omitting it.
 *
 * <p>{@code gate} is the optional overall merge-readiness node (mapped to the CI's required-checks
 * job) rendered as a header badge. It is omitted from the payload when not configured or for
 * fallback repos ({@code @JsonInclude(NON_NULL)}), matching the generated client's optional
 * {@code gate?: Node}. It is intentionally not {@code @Nullable}: that annotation makes springdoc
 * mark the shared {@code Node} schema itself {@code type: "null"}, breaking every other Node use.
 *
 * <p>{@code head} names the commit the node states reflect and whether it is the branch/PR head, so
 * the client can show a freshness anchor ("up to date" vs "newest commit not built yet") — the key
 * to the view being trustworthy. {@code previous} is the immediately-preceding commit's coarse
 * outcome, shown as a confidence footer only while the displayed commit is still running. Both are
 * omitted for the group fallback and when unknown.
 */
public record PipelineDto(
    List<Category> categories,
    @JsonInclude(JsonInclude.Include.NON_NULL) Node gate,
    @JsonInclude(JsonInclude.Include.NON_NULL) Head head,
    @JsonInclude(JsonInclude.Include.NON_NULL) PreviousRun previous) {

  /** A titled group of pipeline nodes (e.g. "Build", "Tests"), rendered in declaration order. */
  public record Category(String name, List<Node> nodes) {}

  /**
   * A single canonical pipeline node with its aggregated status.
   *
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

  /**
   * The commit the pipeline's node states reflect.
   *
   * @param sha the short commit SHA being displayed
   * @param upToDate {@code true} when it is the branch/PR head; {@code false} means the head has no
   *     CI results yet and this is the most recent commit that did run
   */
  public record Head(String sha, boolean upToDate) {}

  /**
   * Coarse outcome of the commit immediately before the displayed one, for at-a-glance confidence
   * while the displayed commit is still running.
   *
   * @param sha the short commit SHA
   * @param conclusion a {@code WorkflowRun.Conclusion} name (e.g. {@code SUCCESS}, {@code FAILURE})
   */
  public record PreviousRun(String sha, String conclusion) {}
}
