package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.workflow.WorkflowJob;
import de.tum.cit.aet.helios.workflow.WorkflowJobRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import jakarta.transaction.Transactional;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Persists GitHub Actions {@code workflow_job} events as {@link WorkflowJob} rows so the pipeline
 * view can render per-stage nodes. A job is only stored when its parent {@link WorkflowRun} is
 * already tracked (the {@code workflow_run_id} foreign key) — jobs for un-ingested runs are
 * dropped, and re-emitted on later job events once the run exists.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubWorkflowJobSyncService {

  private final WorkflowJobRepository workflowJobRepository;
  private final WorkflowRunRepository workflowRunRepository;

  /**
   * Upserts the job carried by the payload. Transactional because it reads the parent run's LAZY
   * {@code repository} association to scope the job to the same tenant.
   */
  @Transactional
  public void syncFromPayload(GitHubWorkflowJobPayload payload) {
    if (payload == null || payload.workflowJob() == null) {
      return;
    }
    final GitHubWorkflowJobPayload.WorkflowJob job = payload.workflowJob();
    if (job.id() == null || job.runId() == null) {
      return;
    }

    final Optional<WorkflowRun> runOpt = workflowRunRepository.findById(job.runId());
    if (runOpt.isEmpty()) {
      // Parent run not tracked (yet). Skip; a later job event for the same run will upsert once
      // the run has been ingested. Avoids a dangling workflow_job with no run.
      log.debug("Skipping workflow_job {} for untracked run {}", job.id(), job.runId());
      return;
    }
    final WorkflowRun run = runOpt.get();

    final WorkflowJob entity = workflowJobRepository.findById(job.id()).orElseGet(WorkflowJob::new);
    entity.setId(job.id());
    entity.setWorkflowRun(run);
    entity.setRepository(run.getRepository());
    entity.setName(job.name());
    entity.setWorkflowName(job.workflowName());
    entity.setStatus(mapStatus(job.status()));
    entity.setConclusion(mapConclusion(job.conclusion()));
    entity.setStartedAt(job.startedAt());
    entity.setCompletedAt(job.completedAt());
    entity.setHtmlUrl(job.htmlUrl());
    entity.setHeadBranch(job.headBranch());
    entity.setHeadSha(job.headSha());
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(job.createdAt());
    }
    entity.setUpdatedAt(job.completedAt() != null ? job.completedAt() : job.startedAt());

    workflowJobRepository.save(entity);
  }

  private static WorkflowRun.Status mapStatus(String status) {
    if (status == null) {
      return null;
    }
    try {
      return WorkflowRun.Status.valueOf(status.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return WorkflowRun.Status.UNKNOWN;
    }
  }

  private static WorkflowRun.Conclusion mapConclusion(String conclusion) {
    if (conclusion == null) {
      return null;
    }
    try {
      return WorkflowRun.Conclusion.valueOf(conclusion.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return WorkflowRun.Conclusion.UNKNOWN;
    }
  }
}
