package de.tum.cit.aet.helios.workflow.pipeline;

import de.tum.cit.aet.helios.workflow.WorkflowJob;
import de.tum.cit.aet.helios.workflow.WorkflowJobRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunDto;
import de.tum.cit.aet.helios.workflow.WorkflowRunService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the canonical pipeline (see {@link PipelineProperties}) for a branch or pull request.
 * Reuses {@link WorkflowRunService} to resolve the head-commit runs (already tenant-scoped), loads
 * their {@link WorkflowJob}s, and aggregates the jobs that match each configured node into a single
 * status. Nodes with no matching job are reported as {@code PENDING}.
 */
@Service
@RequiredArgsConstructor
public class PipelineService {

  private final PipelineProperties properties;
  private final WorkflowRunService workflowRunService;
  private final WorkflowJobRepository workflowJobRepository;

  public PipelineDto getPipelineForBranch(String branchName) {
    return build(workflowRunService.getLatestWorkflowRunsByBranchAndHeadCommitSha(branchName));
  }

  public PipelineDto getPipelineForPullRequest(Long pullRequestId) {
    return build(workflowRunService.getLatestWorkflowRunsByPullRequestIdAndHeadCommit(pullRequestId));
  }

  private PipelineDto build(List<WorkflowRunDto> headRuns) {
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
        jobs.stream()
            .anyMatch(
                job -> {
                  final WorkflowRun.Status s = job.getStatus();
                  return s == WorkflowRun.Status.IN_PROGRESS
                      || s == WorkflowRun.Status.QUEUED
                      || s == WorkflowRun.Status.WAITING
                      || s == WorkflowRun.Status.PENDING
                      || s == WorkflowRun.Status.REQUESTED;
                });
    return anyActive ? WorkflowRun.Status.IN_PROGRESS : WorkflowRun.Status.COMPLETED;
  }

  /** Worst-wins conclusion across the matched jobs. */
  private static WorkflowRun.Conclusion aggregateConclusion(List<WorkflowJob> jobs) {
    if (jobs.stream()
        .anyMatch(
            job -> {
              final WorkflowRun.Conclusion c = job.getConclusion();
              return c == WorkflowRun.Conclusion.FAILURE
                  || c == WorkflowRun.Conclusion.TIMED_OUT
                  || c == WorkflowRun.Conclusion.STARTUP_FAILURE;
            })) {
      return WorkflowRun.Conclusion.FAILURE;
    }
    if (jobs.stream().anyMatch(job -> job.getConclusion() == WorkflowRun.Conclusion.CANCELLED)) {
      return WorkflowRun.Conclusion.CANCELLED;
    }
    if (jobs.stream().allMatch(job -> job.getConclusion() == WorkflowRun.Conclusion.SKIPPED)) {
      return WorkflowRun.Conclusion.SKIPPED;
    }
    if (jobs.stream().anyMatch(job -> job.getConclusion() == WorkflowRun.Conclusion.SUCCESS)) {
      return WorkflowRun.Conclusion.SUCCESS;
    }
    return WorkflowRun.Conclusion.NEUTRAL;
  }
}
