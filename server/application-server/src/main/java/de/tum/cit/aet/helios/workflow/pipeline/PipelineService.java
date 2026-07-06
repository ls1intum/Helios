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
 * lanes; each configured node aggregates the head-commit runs' {@link WorkflowJob}s that match it,
 * and a node with no matching job is reported as {@code PENDING}.
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

  private final WorkflowRunService workflowRunService;
  private final WorkflowJobRepository workflowJobRepository;
  private final PipelineConfigService pipelineConfigService;

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
      return new PipelineDto(List.of());
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
    return new PipelineDto(categories);
  }

  private PipelineDto.Node buildNode(NodeConfig node, List<WorkflowJob> jobs) {
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
}
