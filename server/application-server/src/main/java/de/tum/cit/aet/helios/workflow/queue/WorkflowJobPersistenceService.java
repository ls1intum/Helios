package de.tum.cit.aet.helios.workflow.queue;

import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Upserts a durable {@link WorkflowJob} row from a {@code workflow_job} webhook payload. Runs in
 * parallel to the existing deployment-timing path; see plan §B2.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class WorkflowJobPersistenceService {

  private final WorkflowJobRepository workflowJobRepository;

  @Transactional
  public void upsert(GitHubWorkflowJobPayload payload) {
    if (payload == null || payload.workflowJob() == null) {
      return;
    }
    GitHubWorkflowJobPayload.WorkflowJob src = payload.workflowJob();
    if (src.id() == null || src.runId() == null) {
      return;
    }
    if (payload.repository() == null || payload.repository().id() == null) {
      return;
    }

    WorkflowJob job = workflowJobRepository.findById(src.id()).orElseGet(WorkflowJob::new);

    job.setId(src.id());
    job.setWorkflowRunId(src.runId());
    job.setRepositoryId(payload.repository().id());
    if (src.name() != null) {
      job.setName(src.name());
    } else if (job.getName() == null) {
      job.setName("");
    }
    if (src.workflowName() != null) {
      job.setWorkflowName(src.workflowName());
    }
    if (src.headBranch() != null) {
      job.setHeadBranch(src.headBranch());
    }
    if (src.headSha() != null) {
      job.setHeadSha(src.headSha());
    }
    if (src.status() != null) {
      job.setStatus(src.status());
    }
    if (src.conclusion() != null) {
      job.setConclusion(src.conclusion());
    }
    if (src.createdAt() != null) {
      job.setCreatedAt(src.createdAt());
    }
    if (src.startedAt() != null) {
      job.setStartedAt(src.startedAt());
    }
    if (src.completedAt() != null) {
      job.setCompletedAt(src.completedAt());
    }

    List<String> labels = LabelSets.canonical(src.labels());
    job.setLabels(labels);
    job.setLabelSetHash(LabelSets.hash(labels));
    job.setRunnerKind(LabelSets.deriveRunnerKind(labels));

    if (src.runnerId() != null) {
      job.setRunnerId(src.runnerId());
    }
    if (src.runnerName() != null) {
      job.setRunnerName(src.runnerName());
    }
    if (src.runnerGroupId() != null) {
      job.setRunnerGroupId(src.runnerGroupId());
    }
    if (src.runnerGroupName() != null) {
      job.setRunnerGroupName(src.runnerGroupName());
    }

    job.setQueueWaitSeconds(computeQueueWaitSeconds(job));
    job.setRunDurationSeconds(computeRunDurationSeconds(job));

    workflowJobRepository.save(job);
  }

  private Integer computeQueueWaitSeconds(WorkflowJob job) {
    return durationSeconds(job.getCreatedAt(), job.getStartedAt());
  }

  private Integer computeRunDurationSeconds(WorkflowJob job) {
    return durationSeconds(job.getStartedAt(), job.getCompletedAt());
  }

  private Integer durationSeconds(OffsetDateTime start, OffsetDateTime end) {
    if (start == null || end == null) {
      return null;
    }
    long seconds = ChronoUnit.SECONDS.between(start, end);
    return seconds < 0 ? 0 : (int) seconds;
  }

  public Optional<WorkflowJob> find(Long id) {
    return workflowJobRepository.findById(id);
  }
}
