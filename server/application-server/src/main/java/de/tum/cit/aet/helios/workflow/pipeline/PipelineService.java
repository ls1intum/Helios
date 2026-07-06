package de.tum.cit.aet.helios.workflow.pipeline;

import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitreposettings.WorkflowGroupDto;
import de.tum.cit.aet.helios.gitreposettings.WorkflowGroupService;
import de.tum.cit.aet.helios.gitreposettings.WorkflowMembershipDto;
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

  /** Statuses that mean a matched job has not finished yet (node is still in progress). */
  private static final Set<WorkflowRun.Status> ACTIVE_STATUSES =
      Set.of(
          WorkflowRun.Status.IN_PROGRESS,
          WorkflowRun.Status.QUEUED,
          WorkflowRun.Status.WAITING,
          WorkflowRun.Status.PENDING,
          WorkflowRun.Status.REQUESTED);

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

  public PipelineDto getPipelineForBranch(String branchName) {
    return buildFor(workflowRunService.getLatestWorkflowRunsByBranchAndHeadCommitSha(branchName));
  }

  public PipelineDto getPipelineForPullRequest(Long pullRequestId) {
    return buildFor(
        workflowRunService.getLatestWorkflowRunsByPullRequestIdAndHeadCommit(pullRequestId));
  }

  private PipelineDto buildFor(List<WorkflowRunDto> headRuns) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    return isCanonicalRepository(repositoryId)
        ? buildCanonical(headRuns)
        : buildGrouped(repositoryId, headRuns);
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

  private PipelineDto buildCanonical(List<WorkflowRunDto> headRuns) {
    final List<Long> runIds = headRuns.stream().map(WorkflowRunDto::id).toList();
    // Runs are already scoped to the current repository, so their jobs are too.
    final List<WorkflowJob> jobs =
        runIds.isEmpty() ? List.of() : workflowJobRepository.findByWorkflowRunIdIn(runIds);

    final List<PipelineDto.Category> categories =
        properties.categories().stream()
            .map(
                category ->
                    new PipelineDto.Category(
                        category.name(),
                        category.nodes().stream().map(node -> buildNode(node, jobs)).toList()))
            .toList();
    return new PipelineDto(categories);
  }

  private PipelineDto.Node buildNode(PipelineProperties.Node node, List<WorkflowJob> jobs) {
    final List<WorkflowJob> matched = jobs.stream().filter(job -> matches(node, job)).toList();
    if (matched.isEmpty()) {
      // Not started yet (or never runs for this PR): always-visible, rendered as pending.
      return new PipelineDto.Node(
          node.key(), node.label(), WorkflowRun.Status.PENDING.name(), null, null);
    }

    final WorkflowRun.Status status = aggregateStatus(matched);
    final WorkflowRun.Conclusion conclusion =
        status == WorkflowRun.Status.COMPLETED ? aggregateConclusion(matched) : null;
    final String htmlUrl =
        matched.stream()
            .map(WorkflowJob::getHtmlUrl)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    return new PipelineDto.Node(
        node.key(),
        node.label(),
        status.name(),
        conclusion == null ? null : conclusion.name(),
        htmlUrl);
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

  /** Any not-yet-completed matched job keeps the node in progress; otherwise it is completed. */
  private static WorkflowRun.Status aggregateStatus(List<WorkflowJob> jobs) {
    final boolean anyActive =
        jobs.stream().map(WorkflowJob::getStatus).anyMatch(ACTIVE_STATUSES::contains);
    return anyActive ? WorkflowRun.Status.IN_PROGRESS : WorkflowRun.Status.COMPLETED;
  }

  /** Worst-wins conclusion across the matched jobs. */
  private static WorkflowRun.Conclusion aggregateConclusion(List<WorkflowJob> jobs) {
    final Set<WorkflowRun.Conclusion> present =
        jobs.stream()
            .map(WorkflowJob::getConclusion)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (present.stream().anyMatch(FAILED_CONCLUSIONS::contains)) {
      return WorkflowRun.Conclusion.FAILURE;
    }
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
      return new PipelineDto(List.of());
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
    return new PipelineDto(categories);
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
