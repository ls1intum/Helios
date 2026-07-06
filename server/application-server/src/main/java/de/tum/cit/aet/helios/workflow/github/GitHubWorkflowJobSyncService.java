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
    entity.setStatus(
        parseOrUnknown(WorkflowRun.Status.class, job.status(), WorkflowRun.Status.UNKNOWN));
    entity.setConclusion(
        parseOrUnknown(
            WorkflowRun.Conclusion.class, job.conclusion(), WorkflowRun.Conclusion.UNKNOWN));
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

  /**
   * Parses a GitHub status/conclusion string into the matching enum constant (case-insensitive),
   * falling back to {@code unknown} for an unrecognised value and {@code null} for a missing one.
   */
  private static <E extends Enum<E>> E parseOrUnknown(Class<E> type, String value, E unknown) {
    if (value == null) {
      return null;
    }
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return unknown;
    }
  }
}
