package de.tum.cit.aet.helios.workflow.pipeline;

import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.workflow.WorkflowJob;
import de.tum.cit.aet.helios.workflow.WorkflowJobRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunDto;
import de.tum.cit.aet.helios.workflow.WorkflowRunService;
import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto;
import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto.NodeConfig;
import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the pipeline for a branch or pull request from the current repository's per-repo
 * configuration ({@link PipelineConfigService}). Every repository gets the Build/Test/Quality
 * lanes; each configured node aggregates the head-commit runs' {@link WorkflowJob}s that match it.
 *
 * <p>Node states are distinct: a node with no matching job (or only queued jobs) is {@code PENDING}
 * (not running yet), and a failing leg flips the node to {@code FAILURE} immediately (fail-fast),
 * even while slower legs run, so the earliest actionable signal isn't hidden behind a spinner. An
 * optional global {@code gate} node ({@link PipelineProperties}) is surfaced as a header badge.
 */
@Service
@RequiredArgsConstructor
public class PipelineService {

  /** Conclusions that count as a failure for fail-fast + worst-wins aggregation. */
  private static final Set<WorkflowRun.Conclusion> FAILED_CONCLUSIONS =
      Set.of(
          WorkflowRun.Conclusion.FAILURE,
          WorkflowRun.Conclusion.TIMED_OUT,
          WorkflowRun.Conclusion.STARTUP_FAILURE);

  private final WorkflowRunService workflowRunService;
  private final WorkflowJobRepository workflowJobRepository;
  private final PipelineConfigService pipelineConfigService;
  private final PipelineProperties properties;

  public PipelineDto getPipelineForBranch(String branchName) {
    return build(workflowRunService.getLatestWorkflowRunsByBranchAndHeadCommitSha(branchName));
  }

  public PipelineDto getPipelineForPullRequest(Long pullRequestId) {
    return build(
        workflowRunService.getLatestWorkflowRunsByPullRequestIdAndHeadCommit(pullRequestId));
  }

  private PipelineDto build(List<WorkflowRunDto> headRuns) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null) {
      return new PipelineDto(List.of(), null);
    }
    final PipelineConfigDto config = pipelineConfigService.getConfig(repositoryId);

    final List<Long> runIds = headRuns.stream().map(WorkflowRunDto::id).toList();
    // Runs are already scoped to the current repository, so their jobs are too.
    final List<WorkflowJob> jobs =
        runIds.isEmpty() ? List.of() : workflowJobRepository.findByWorkflowRunIdIn(runIds);

    final List<PipelineDto.Category> categories =
        config.categories().stream()
            .map(
                category ->
                    new PipelineDto.Category(
                        category.name(),
                        category.nodes().stream().map(node -> buildNode(node, jobs)).toList()))
            .toList();

    // Optional overall merge-gate node (e.g. Artemis' "All required CI Passed"), a header badge.
    final PipelineDto.Node gate =
        properties.gate() == null ? null : buildNode(properties.gate(), jobs);
    return new PipelineDto(categories, gate);
  }

  /**
   * Aggregates the CI jobs matching a node into a single status. Two boundaries are deliberate:
   * queued-only jobs stay {@code PENDING} (not-running-yet), distinct from a running node; and a
   * failing leg flips the node to {@code FAILURE} <i>immediately</i> (fail-fast), even while slower
   * legs run, so the earliest actionable signal is never hidden behind the spinner.
   */
  private PipelineDto.Node buildNode(NodeConfig node, List<WorkflowJob> jobs) {
    final List<WorkflowJob> matched = jobs.stream().filter(job -> matches(node, job)).toList();
    if (matched.isEmpty()) {
      // Not started yet (or never runs for this PR): always-visible, rendered as "not running yet".
      return new PipelineDto.Node(
          node.key(), node.label(), WorkflowRun.Status.PENDING.name(), null, null);
    }

    final boolean allTerminal =
        matched.stream().allMatch(job -> job.getStatus() == WorkflowRun.Status.COMPLETED);
    final boolean anyFailed = matched.stream().anyMatch(PipelineService::isFailed);

    final WorkflowRun.Status status;
    final WorkflowRun.Conclusion conclusion;
    if (anyFailed) {
      // Fail-fast: conclusion reads FAILURE the instant a leg fails; status stays IN_PROGRESS until
      // every leg is terminal (truthful for other consumers). The client checks conclusion before
      // status, so a failing node shows a static red X immediately, never a spinner.
      status = allTerminal ? WorkflowRun.Status.COMPLETED : WorkflowRun.Status.IN_PROGRESS;
      conclusion = WorkflowRun.Conclusion.FAILURE;
    } else if (allTerminal) {
      status = WorkflowRun.Status.COMPLETED;
      conclusion = aggregateConclusion(matched);
    } else if (matched.stream().anyMatch(PipelineService::hasStarted)) {
      status = WorkflowRun.Status.IN_PROGRESS;
      conclusion = null;
    } else {
      status = WorkflowRun.Status.PENDING;
      conclusion = null;
    }

    return new PipelineDto.Node(
        node.key(),
        node.label(),
        status.name(),
        conclusion == null ? null : conclusion.name(),
        pickHtmlUrl(matched, conclusion));
  }

  private static boolean hasStarted(WorkflowJob job) {
    return job.getStatus() == WorkflowRun.Status.IN_PROGRESS
        || job.getStatus() == WorkflowRun.Status.COMPLETED;
  }

  /** Null-safe failure test; FAILED_CONCLUSIONS is a Set.of that throws on contains(null). */
  private static boolean isFailed(WorkflowJob job) {
    return job.getConclusion() != null && FAILED_CONCLUSIONS.contains(job.getConclusion());
  }

  /**
   * On a failure, links to a job carrying the failing conclusion so the click-through opens the
   * failing leg, not an arbitrary passing one; otherwise the first job with a URL.
   */
  private static String pickHtmlUrl(List<WorkflowJob> matched, WorkflowRun.Conclusion conclusion) {
    if (conclusion == WorkflowRun.Conclusion.FAILURE) {
      final String failing =
          matched.stream()
              .filter(PipelineService::isFailed)
              .map(WorkflowJob::getHtmlUrl)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);
      if (failing != null) {
        return failing;
      }
    }
    return matched.stream()
        .map(WorkflowJob::getHtmlUrl)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static boolean matches(NodeConfig node, WorkflowJob job) {
    if (job.getName() == null) {
      return false;
    }
    final String matcher = node.workflowNameMatcher();
    if (matcher != null && !matcher.isBlank()) {
      final String workflowName = job.getWorkflowName();
      if (workflowName == null
          || !workflowName.toLowerCase(Locale.ROOT).contains(matcher.toLowerCase(Locale.ROOT))) {
        return false;
      }
    }
    final String jobName = job.getName().toLowerCase(Locale.ROOT);
    return node.jobNameMatchers().stream()
        .anyMatch(prefix -> jobName.startsWith(prefix.toLowerCase(Locale.ROOT)));
  }

  /**
   * Worst-wins conclusion for terminal, non-failing jobs. Failures are handled earlier by
   * {@link #buildNode} (fail-fast), so this is only reached when no matched job failed. {@code
   * CANCELLED}/{@code SKIPPED} render neutral-grey; {@code ACTION_REQUIRED}/{@code STALE} fold into
   * {@code NEUTRAL} — the model has no warning state.
   */
  private static WorkflowRun.Conclusion aggregateConclusion(List<WorkflowJob> jobs) {
    final Set<WorkflowRun.Conclusion> present =
        jobs.stream()
            .map(WorkflowJob::getConclusion)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (present.contains(WorkflowRun.Conclusion.CANCELLED)) {
      return WorkflowRun.Conclusion.CANCELLED;
    }
    if (present.equals(Set.of(WorkflowRun.Conclusion.SKIPPED))) {
      return WorkflowRun.Conclusion.SKIPPED;
    }
    if (present.contains(WorkflowRun.Conclusion.SUCCESS)) {
      return WorkflowRun.Conclusion.SUCCESS;
    }
    return WorkflowRun.Conclusion.NEUTRAL;
  }
}
