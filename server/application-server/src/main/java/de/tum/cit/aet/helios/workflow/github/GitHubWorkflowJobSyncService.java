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

  /** Length of the {@code varchar(255)} columns of {@code workflow_job}. */
  private static final int MAX_COLUMN_LENGTH = 255;

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
    entity.setName(truncate(job.name(), "name", job.id()));
    entity.setWorkflowName(truncate(job.workflowName(), "workflowName", job.id()));
    entity.setStatus(
        parseOrUnknown(WorkflowRun.Status.class, job.status(), WorkflowRun.Status.UNKNOWN));
    entity.setConclusion(
        parseOrUnknown(
            WorkflowRun.Conclusion.class, job.conclusion(), WorkflowRun.Conclusion.UNKNOWN));
    entity.setStartedAt(job.startedAt());
    entity.setCompletedAt(job.completedAt());
    entity.setHtmlUrl(truncate(job.htmlUrl(), "htmlUrl", job.id()));
    entity.setHeadBranch(truncate(job.headBranch(), "headBranch", job.id()));
    entity.setHeadSha(truncate(job.headSha(), "headSha", job.id()));
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(job.createdAt());
    }
    entity.setUpdatedAt(job.completedAt() != null ? job.completedAt() : job.startedAt());

    workflowJobRepository.save(entity);
  }

  /**
   * Cuts a text value to the {@code varchar(255)} column length. GitHub does not bound these
   * values; for example, a job whose {@code name:} is an expression that GitHub could not
   * evaluate is reported with the raw expression text (445 characters in Artemis' CI), which would
   * otherwise make the insert fail and drop the whole job event. Prefixes are preserved, so
   * matching jobs by name prefix keeps working.
   */
  static String truncate(String value, String fieldName, long jobId) {
    if (value == null || value.length() <= MAX_COLUMN_LENGTH) {
      return value;
    }
    int end = MAX_COLUMN_LENGTH;
    // Do not leave half of a surrogate pair at the end.
    if (Character.isHighSurrogate(value.charAt(end - 1))) {
      end--;
    }
    log.info(
        "Truncated workflow job field '{}' from {} to {} characters for job {}",
        fieldName,
        value.length(),
        end,
        jobId);
    return value.substring(0, end);
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
