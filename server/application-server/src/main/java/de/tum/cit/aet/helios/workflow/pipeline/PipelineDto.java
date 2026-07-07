package de.tum.cit.aet.helios.workflow.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
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
 * to the view being trustworthy. {@code previous} is the last built commit's outcome (see its
 * record). Both are {@code @JsonInclude(NON_NULL)} and omitted for the group fallback.
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
   * The commit the pipeline's node states reflect, with enough context to recognise and open it.
   *
   * @param sha the short commit SHA being displayed
   * @param upToDate {@code true} when it is the branch/PR head; {@code false} means the head has no
   *     CI results yet and this is the most recent commit that did run
   * @param message the commit subject (first line), when known; lets the developer recognise the
   *     commit without clicking through
   * @param authoredAt when the commit was authored, when known (rendered as relative time)
   * @param htmlUrl link to the commit on GitHub, so the SHA is actionable rather than dead text
   */
  public record Head(
      String sha,
      boolean upToDate,
      @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String message,
      @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable OffsetDateTime authoredAt,
      @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String htmlUrl) {}

  /**
   * The last <i>definitive</i> result (pass or fail) in recent history, for at-a-glance confidence
   * while the displayed commit is still running. Inconclusive outcomes — cancelled/superseded,
   * skipped, still-running — are walked past rather than shown, since they carry no confidence
   * signal (on PR CI a cancelled run usually just means it was superseded by a newer push).
   *
   * @param sha the short commit SHA
   * @param conclusion {@code SUCCESS} or {@code FAILURE}
   * @param htmlUrl link to that commit's CI run on GitHub (the failing run when it failed)
   */
  public record PreviousRun(
      String sha,
      String conclusion,
      @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String htmlUrl) {}
}
