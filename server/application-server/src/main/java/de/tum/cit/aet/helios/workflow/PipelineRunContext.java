package de.tum.cit.aet.helios.workflow;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * The resolved run context a pipeline is built from.
 *
 * <p>Rather than pinning strictly to the branch/PR head commit — which shows an all-"not running
 * yet" skeleton whenever that commit's CI run is still queued, gated, or hasn't been ingested yet —
 * this captures the commit actually worth displaying, whether it is the head ({@code upToDate}), and
 * a coarse outcome for the immediately-preceding commit (rendered as a confidence footer while the
 * displayed commit is still running).
 *
 * <p>Every field is derived purely from ingested {@link WorkflowRun} rows — no GitHub API calls — so
 * the pipeline stays exactly as fresh as the webhook stream.
 *
 * @param currentRuns the head runs (latest per workflow) for {@code displayedSha}
 * @param displayedSha the commit the node states reflect, or {@code null} when nothing is known
 * @param upToDate whether {@code displayedSha} is the branch/PR head; {@code false} means the head
 *     has no runs yet and an earlier commit is shown instead
 * @param previousSha the commit immediately before {@code displayedSha}, or {@code null}
 * @param previousRuns the runs (latest per workflow) for {@code previousSha}
 */
public record PipelineRunContext(
    List<WorkflowRunDto> currentRuns,
    @Nullable String displayedSha,
    boolean upToDate,
    @Nullable String previousSha,
    List<WorkflowRunDto> previousRuns) {

  public PipelineRunContext {
    currentRuns = currentRuns == null ? List.of() : currentRuns;
    previousRuns = previousRuns == null ? List.of() : previousRuns;
  }

  /** No branch/PR context (e.g. unknown branch): an empty, up-to-date pipeline. */
  public static PipelineRunContext empty() {
    return new PipelineRunContext(List.of(), null, true, null, List.of());
  }
}
