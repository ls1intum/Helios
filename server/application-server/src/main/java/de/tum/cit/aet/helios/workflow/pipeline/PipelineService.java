package de.tum.cit.aet.helios.workflow.pipeline;

import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitreposettings.WorkflowGroupDto;
import de.tum.cit.aet.helios.gitreposettings.WorkflowGroupService;
import de.tum.cit.aet.helios.gitreposettings.WorkflowMembershipDto;
import de.tum.cit.aet.helios.workflow.PipelineRunContext;
import de.tum.cit.aet.helios.workflow.WorkflowJob;
import de.tum.cit.aet.helios.workflow.WorkflowJobRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunDto;
import de.tum.cit.aet.helios.workflow.WorkflowRunService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the pipeline for a branch or pull request.
 *
 * <p>For repositories listed in {@code helios.pipeline.repositories} the canonical, always-visible
 * node catalog (see {@link PipelineProperties}) is used: the head-commit runs' {@link WorkflowJob}s
 * are matched against each configured node and aggregated. Every other repository falls back to
 * the previous behaviour — its {@code WorkflowGroup}s rendered as categories of workflow-run nodes
 * — so a repository whose CI job names don't match the (Artemis-shaped) catalog keeps a meaningful
 * pipeline instead of an all-pending skeleton.
 */
@Service
@RequiredArgsConstructor
public class PipelineService {

  /** Conclusions that count as a failure for worst-wins aggregation. */
  private static final Set<WorkflowRun.Conclusion> FAILED_CONCLUSIONS =
      Set.of(
          WorkflowRun.Conclusion.FAILURE,
          WorkflowRun.Conclusion.TIMED_OUT,
          WorkflowRun.Conclusion.STARTUP_FAILURE);

  private final PipelineProperties properties;
  private final WorkflowRunService workflowRunService;
  private final WorkflowJobRepository workflowJobRepository;
  private final WorkflowGroupService workflowGroupService;
  private final GitRepoRepository gitRepoRepository;
  private final CommitRepository commitRepository;

  public PipelineDto getPipelineForBranch(String branchName) {
    return buildFor(workflowRunService.getPipelineRunContextForBranch(branchName));
  }

  public PipelineDto getPipelineForPullRequest(Long pullRequestId) {
    return buildFor(workflowRunService.getPipelineRunContextForPullRequest(pullRequestId));
  }

  private PipelineDto buildFor(PipelineRunContext context) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    return isCanonicalRepository(repositoryId)
        ? buildCanonical(repositoryId, context)
        : buildGrouped(repositoryId, context.currentRuns());
  }

  /** Whether the current repository uses the canonical node catalog (vs. the group fallback). */
  private boolean isCanonicalRepository(Long repositoryId) {
    if (repositoryId == null || properties.repositories().isEmpty()) {
      return false;
    }
    return gitRepoRepository
        .findByRepositoryId(repositoryId)
        .map(GitRepository::getNameWithOwner)
        .map(properties.repositories()::contains)
        .orElse(false);
  }

  // --- Canonical catalog (config-driven Build/Tests/Quality nodes) -----------------------------

  private PipelineDto buildCanonical(Long repositoryId, PipelineRunContext context) {
    final List<WorkflowRunDto> currentRuns = context.currentRuns();
    final List<Long> runIds = currentRuns.stream().map(WorkflowRunDto::id).toList();
    // Runs are already scoped to the current repository, so their jobs are too.
    final List<WorkflowJob> jobs =
        runIds.isEmpty()
            ? List.of()
            : latestAttemptPerJob(workflowJobRepository.findByWorkflowRunIdIn(runIds));

    final List<PipelineDto.Category> categories =
        properties.categories().stream()
            .map(
                category ->
                    new PipelineDto.Category(
                        category.name(),
                        category.nodes().stream()
                            .map(node -> buildNode(node, jobs, currentRuns))
                            .toList()))
            .toList();
    // Optional overall merge-gate node (e.g. Artemis' "All required CI Passed"), surfaced as a
    // header badge on the client. Absent for the group fallback.
    final PipelineDto.Node gate =
        properties.gate() == null ? null : buildNode(properties.gate(), jobs, currentRuns);

    final PipelineDto.Head head =
        context.displayedSha() == null
            ? null
            : buildHead(repositoryId, context.displayedSha(), context.upToDate());
    final PipelineDto.PreviousRun previous = previousRun(repositoryId, context);
    return new PipelineDto(categories, gate, head, previous);
  }

  /** The freshness anchor: short SHA, subject line, author time, and a link to the commit. */
  private PipelineDto.Head buildHead(Long repositoryId, String fullSha, boolean upToDate) {
    final var commit = commitRepository.findByShaAndRepositoryRepositoryId(fullSha, repositoryId);
    return new PipelineDto.Head(
        shortSha(fullSha),
        upToDate,
        commit.map(c -> firstLine(c.getMessage())).orElse(null),
        commit.map(Commit::getAuthoredAt).orElse(null),
        commitUrl(repositoryId, fullSha));
  }

  /** Link to a commit on GitHub, or null when the repository's {@code nameWithOwner} is unknown. */
  private String commitUrl(Long repositoryId, String fullSha) {
    return gitRepoRepository
        .findByRepositoryId(repositoryId)
        .map(GitRepository::getNameWithOwner)
        .map(nameWithOwner -> "https://github.com/" + nameWithOwner + "/commit/" + fullSha)
        .orElse(null);
  }

  /** Commit subject (first non-blank line), or null. */
  private static String firstLine(String message) {
    if (message == null || message.isBlank()) {
      return null;
    }
    final int newline = message.indexOf('\n');
    return (newline < 0 ? message : message.substring(0, newline)).strip();
  }

  /**
   * The previous commit's outcome for the confidence footer — only a <i>definitive</i> pass/fail is
   * a useful signal, so anything else (the resolver already walks past inconclusive commits, but we
   * guard here too) yields no footer rather than a meaningless "cancelled".
   *
   * <p>The outcome and link are scoped to the pipeline's CI run via {@link #pipelineScopedRuns}, so
   * an unrelated workflow on the same commit (Codacy, PR labeler, coverage) can neither set the
   * pass/fail label nor be linked in place of the CI run.
   */
  private PipelineDto.PreviousRun previousRun(Long repositoryId, PipelineRunContext context) {
    if (context.previousSha() == null) {
      return null;
    }
    final List<WorkflowRunDto> ciRuns = pipelineScopedRuns(context.previousRuns());
    final String conclusion = aggregateRunConclusion(ciRuns);
    if (!WorkflowRun.Conclusion.SUCCESS.name().equals(conclusion)
        && !WorkflowRun.Conclusion.FAILURE.name().equals(conclusion)) {
      return null;
    }
    final String runUrl = previousRunUrl(ciRuns, conclusion);
    return new PipelineDto.PreviousRun(
        shortSha(context.previousSha()),
        conclusion,
        runUrl != null ? runUrl : commitUrl(repositoryId, context.previousSha()));
  }

  /**
   * Narrows a commit's runs to the pipeline's CI workflow — the one the nodes and gate reflect,
   * identified by the gate's {@code workflowNameMatcher}. Without a configured matcher (a
   * non-canonical setup) all runs are kept.
   */
  private List<WorkflowRunDto> pipelineScopedRuns(List<WorkflowRunDto> runs) {
    final String matcher =
        properties.gate() == null ? null : properties.gate().workflowNameMatcher();
    if (matcher == null || matcher.isBlank()) {
      return runs;
    }
    final String needle = matcher.toLowerCase(Locale.ROOT);
    return runs.stream()
        .filter(run -> run.name() != null && run.name().toLowerCase(Locale.ROOT).contains(needle))
        .toList();
  }

  /** Links the footer to the previous commit's CI run — the failing run when it failed. */
  private static String previousRunUrl(List<WorkflowRunDto> runs, String conclusion) {
    if (WorkflowRun.Conclusion.FAILURE.name().equals(conclusion)) {
      final String failing =
          runs.stream()
              .filter(PipelineService::isFailedRun)
              .map(WorkflowRunDto::htmlUrl)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);
      if (failing != null) {
        return failing;
      }
    }
    return runs.stream()
        .map(WorkflowRunDto::htmlUrl)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Worst-wins outcome over a commit's CI runs, from run-level conclusions only (no job fetch). Any
   * failure wins; a still-running run yields {@code null} (no confident signal, footer hidden).
   */
  private static String aggregateRunConclusion(List<WorkflowRunDto> runs) {
    if (runs.isEmpty()) {
      return null;
    }
    if (runs.stream().anyMatch(PipelineService::isFailedRun)) {
      return WorkflowRun.Conclusion.FAILURE.name();
    }
    if (runs.stream().anyMatch(r -> r.status() != WorkflowRun.Status.COMPLETED)) {
      return null;
    }
    // Cancelled before success, mirroring the node-level aggregator's worst-wins precedence.
    if (runs.stream().anyMatch(r -> r.conclusion() == WorkflowRun.Conclusion.CANCELLED)) {
      return WorkflowRun.Conclusion.CANCELLED.name();
    }
    if (runs.stream().anyMatch(r -> r.conclusion() == WorkflowRun.Conclusion.SUCCESS)) {
      return WorkflowRun.Conclusion.SUCCESS.name();
    }
    return WorkflowRun.Conclusion.NEUTRAL.name();
  }

  private static String shortSha(String sha) {
    return sha.length() <= 7 ? sha : sha.substring(0, 7);
  }

  /**
   * Aggregates the CI jobs matching a node into a single status. Two boundaries are deliberate:
   * queued-only jobs stay {@code PENDING} (not-running-yet), distinct from a running node; and a
   * failing leg flips the node to {@code FAILURE} <i>immediately</i> (fail-fast), even while slower
   * legs run, so the earliest actionable signal is never hidden behind the spinner.
   */
  private PipelineDto.Node buildNode(
      PipelineProperties.Node node, List<WorkflowJob> jobs, List<WorkflowRunDto> currentRuns) {
    final List<WorkflowJob> matched = jobs.stream().filter(job -> matches(node, job)).toList();
    if (matched.isEmpty()) {
      // No job yet — infer a meaningful state from the node's CI run instead of a dead "pending".
      return emptyNode(node, currentRuns);
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
      // Matched jobs exist but none have started — they are scheduled, so "queued", not idle.
      status = WorkflowRun.Status.QUEUED;
      conclusion = null;
    }

    return new PipelineDto.Node(
        node.key(),
        node.label(),
        status.name(),
        conclusion == null ? null : conclusion.name(),
        pickHtmlUrl(matched, conclusion));
  }

  /**
   * Collapses re-run attempts. GitHub assigns a new job id per attempt but keeps the run id, and
   * ingestion never deletes the prior attempt, so a run can carry both a failed attempt-1 job and a
   * passed attempt-2 job under the same name. Keep only the latest attempt (highest job id) per
   * (run, job name) so a green re-run is not reported as a permanent failure.
   */
  private static List<WorkflowJob> latestAttemptPerJob(List<WorkflowJob> jobs) {
    return List.copyOf(
        jobs.stream()
            .collect(
                Collectors.toMap(
                    job -> job.getWorkflowRun().getId() + " " + job.getName(),
                    job -> job,
                    (a, b) -> a.getId() >= b.getId() ? a : b))
            .values());
  }

  private static boolean hasStarted(WorkflowJob job) {
    return job.getStatus() == WorkflowRun.Status.IN_PROGRESS
        || job.getStatus() == WorkflowRun.Status.COMPLETED;
  }

  /** Null-safe failure test; FAILED_CONCLUSIONS is a Set.of that throws on contains(null). */
  private static boolean isFailed(WorkflowJob job) {
    return job.getConclusion() != null && FAILED_CONCLUSIONS.contains(job.getConclusion());
  }

  /** Null-safe failure test for a run's conclusion (mirrors {@link #isFailed(WorkflowJob)}). */
  private static boolean isFailedRun(WorkflowRunDto run) {
    return run.conclusion() != null && FAILED_CONCLUSIONS.contains(run.conclusion());
  }

  /**
   * On a failure, links to a job carrying the failing conclusion so the click-through opens the
   * failing leg, not an arbitrary passing one; otherwise the first job with a URL.
   */
  private static String pickHtmlUrl(
      List<WorkflowJob> matched, WorkflowRun.Conclusion conclusion) {
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

  private static boolean matches(PipelineProperties.Node node, WorkflowJob job) {
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
   * State for a node that has no matching job yet, inferred from the CI run it belongs to so the
   * view stays honest rather than showing a permanent "not running yet".
   *
   * <ul>
   *   <li>run awaiting approval → {@code ACTION_REQUIRED} ("waiting for approval") — actionable;
   *   <li>run in progress → {@code IN_PROGRESS} — mirror the run so an active pipeline reads as
   *       running, not idle (the job's exact state backfills once its events are ingested; jobs on
   *       a busy CI often lag the run), rather than under-stating a running stage as merely queued;
   *   <li>run queued but nothing started → {@code QUEUED} ("queued") — scheduled, not idle;
   *   <li>run completed but this job never appeared → {@code NEUTRAL} ("no result") — we cannot
   *       tell an intentional skip from an event we never ingested, so we make the weaker claim;
   *   <li>no run matches this node at all → {@code PENDING} ("not running yet").
   * </ul>
   */
  private static PipelineDto.Node emptyNode(
      PipelineProperties.Node node, List<WorkflowRunDto> currentRuns) {
    final List<WorkflowRunDto> runs =
        currentRuns.stream().filter(run -> matchesWorkflow(node, run)).toList();

    final WorkflowRun.Status status;
    WorkflowRun.Conclusion conclusion = null;
    if (runs.isEmpty()) {
      status = WorkflowRun.Status.PENDING;
    } else if (runs.stream().anyMatch(PipelineService::isAwaitingApproval)) {
      status = WorkflowRun.Status.WAITING;
      conclusion = WorkflowRun.Conclusion.ACTION_REQUIRED;
    } else if (runs.stream().anyMatch(run -> run.status() == WorkflowRun.Status.IN_PROGRESS)) {
      status = WorkflowRun.Status.IN_PROGRESS;
    } else if (runs.stream().anyMatch(run -> run.status() != WorkflowRun.Status.COMPLETED)) {
      status = WorkflowRun.Status.QUEUED;
    } else {
      status = WorkflowRun.Status.COMPLETED;
      conclusion = WorkflowRun.Conclusion.NEUTRAL;
    }
    return new PipelineDto.Node(
        node.key(),
        node.label(),
        status.name(),
        conclusion == null ? null : conclusion.name(),
        null);
  }

  /** Whether a run belongs to a node, by the node's {@code workflowNameMatcher} (blank = any). */
  private static boolean matchesWorkflow(PipelineProperties.Node node, WorkflowRunDto run) {
    final String matcher = node.workflowNameMatcher();
    if (matcher == null || matcher.isBlank()) {
      return true;
    }
    final String name = run.name();
    return name != null
        && name.toLowerCase(Locale.ROOT).contains(matcher.toLowerCase(Locale.ROOT));
  }

  private static boolean isAwaitingApproval(WorkflowRunDto run) {
    return run.status() == WorkflowRun.Status.WAITING
        || run.status() == WorkflowRun.Status.ACTION_REQUIRED
        || run.conclusion() == WorkflowRun.Conclusion.ACTION_REQUIRED;
  }

  /**
   * Worst-wins conclusion for terminal, non-failing jobs. Failures are handled earlier by
   * {@link #buildNode} (fail-fast), so this is only reached when no matched job failed. The split
   * mirrors the client's status colours: {@code CANCELLED} and {@code SKIPPED} render neutral-grey
   * (unlike {@code TIMED_OUT}/{@code STARTUP_FAILURE}, which are failures), and {@code
   * ACTION_REQUIRED}/{@code STALE} fold into {@code NEUTRAL} — the model has no warning state.
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

  // --- Group fallback (previous behaviour for non-canonical repositories) ----------------------

  private PipelineDto buildGrouped(Long repositoryId, List<WorkflowRunDto> headRuns) {
    if (repositoryId == null) {
      return new PipelineDto(List.of(), null, null, null);
    }
    // getLatest... returns the latest run per workflowId, so a workflowId maps to one run.
    final Map<Long, WorkflowRunDto> runByWorkflowId = new HashMap<>();
    for (WorkflowRunDto run : headRuns) {
      runByWorkflowId.putIfAbsent(run.workflowId(), run);
    }

    final Set<Long> groupedWorkflowIds = new HashSet<>();
    final List<PipelineDto.Category> categories = new ArrayList<>();

    final List<WorkflowGroupDto> groups =
        workflowGroupService.getAllWorkflowGroupsByRepositoryId(repositoryId).stream()
            .sorted(Comparator.comparing(WorkflowGroupDto::orderIndex))
            .toList();
    for (WorkflowGroupDto group : groups) {
      final List<WorkflowMembershipDto> memberships =
          group.memberships() == null ? List.of() : group.memberships();
      final List<PipelineDto.Node> nodes = new ArrayList<>();
      for (WorkflowMembershipDto membership :
          memberships.stream().sorted(Comparator.comparing(WorkflowMembershipDto::orderIndex))
              .toList()) {
        groupedWorkflowIds.add(membership.workflowId());
        final WorkflowRunDto run = runByWorkflowId.get(membership.workflowId());
        if (run != null) {
          nodes.add(runNode(run));
        }
      }
      if (!nodes.isEmpty()) {
        categories.add(new PipelineDto.Category(group.name(), nodes));
      }
    }

    // Runs whose workflow is in no group surface under an "Ungrouped" category (as before).
    final List<PipelineDto.Node> ungrouped =
        headRuns.stream()
            .filter(run -> !groupedWorkflowIds.contains(run.workflowId()))
            .map(PipelineService::runNode)
            .toList();
    if (!ungrouped.isEmpty()) {
      categories.add(new PipelineDto.Category("Ungrouped", ungrouped));
    }
    return new PipelineDto(categories, null, null, null);
  }

  private static PipelineDto.Node runNode(WorkflowRunDto run) {
    return new PipelineDto.Node(
        "run-" + run.id(),
        run.name(),
        run.status() == null ? null : run.status().name(),
        run.conclusion() == null ? null : run.conclusion().name(),
        run.htmlUrl());
  }
}
