package de.tum.cit.aet.helios.workflow.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentWorkflowJobTimingMeta;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubWorkflowJobTimingService {

  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final Cache<Long, RunRelevance> runRelevanceCache =
      Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofHours(1)).build();

  @Transactional
  public void persistDurations(GitHubWorkflowJobPayload payload) {
    if (payload == null || payload.workflowJob() == null) {
      return;
    }

    GitHubWorkflowJobPayload.WorkflowJob job = payload.workflowJob();
    if (job.runId() == null) {
      return;
    }

    if ("queued".equalsIgnoreCase(payload.action()) && payload.deployment() == null) {
      return;
    }

    RunRelevance cachedRelevance = getCachedRelevance(job.runId());
    if (cachedRelevance != null) {
      if (!(cachedRelevance instanceof Relevant relevance)) {
        return;
      }
      if (canSkipFromCache(payload, relevance)) {
        return;
      }
      processRelevantRun(job.runId(), payload, relevance);
      return;
    }

    RunRelevance resolvedRelevance = resolveRelevance(job.runId());
    runRelevanceCache.put(job.runId(), resolvedRelevance);
    if (!(resolvedRelevance instanceof Relevant relevance)) {
      return;
    }

    if (canSkipFromCache(payload, relevance)) {
      return;
    }

    processRelevantRun(job.runId(), payload, relevance);
  }

  private void processRelevantRun(
      Long runId, GitHubWorkflowJobPayload payload, Relevant relevance) {
    Optional<HeliosDeployment> heliosDeploymentOpt =
        heliosDeploymentRepository.findById(relevance.heliosDeploymentId());
    if (heliosDeploymentOpt.isEmpty()) {
      runRelevanceCache.invalidate(runId);
      return;
    }

    HeliosDeployment heliosDeployment = heliosDeploymentOpt.get();
    boolean changed = applyTimingChanges(heliosDeployment, relevance, payload);

    if (changed) {
      heliosDeploymentRepository.save(heliosDeployment);
    }
    runRelevanceCache.put(runId, relevanceFromDeployment(relevance, heliosDeployment));
  }

  private boolean applyTimingChanges(
      HeliosDeployment heliosDeployment,
      Relevant relevance,
      GitHubWorkflowJobPayload payload) {
    GitHubWorkflowJobPayload.WorkflowJob job = payload.workflowJob();
    GitHubWorkflowJobPayload.Deployment deployment = payload.deployment();

    boolean changed =
        persistWorkflowStart(heliosDeployment, relevance.workflowRunStartedAt(), payload);

    if (!relevance.deployJobName().equals(job.name())) {
      return changed;
    }

    if (deployment != null
        && heliosDeployment.getDeploymentId() == null
        && deployment.id() != null) {
      heliosDeployment.setDeploymentId(deployment.id());
      changed = true;
    }

    if (isJobStartedEvent(payload.action(), job)) {
      if (heliosDeployment.getDeployJobStartedAt() == null) {
        heliosDeployment.setDeployJobStartedAt(job.startedAt());
        changed = true;
      }
      return changed;
    }

    if (!isCompletedEvent(payload.action(), job)) {
      return changed;
    }

    if (heliosDeployment.getDeployJobStartedAt() == null && job.startedAt() != null) {
      heliosDeployment.setDeployJobStartedAt(job.startedAt());
      changed = true;
    }

    if (job.startedAt() == null || job.completedAt() == null) {
      return changed;
    }

    OffsetDateTime deployJobStartedAt =
        heliosDeployment.getDeployJobStartedAt() != null
            ? heliosDeployment.getDeployJobStartedAt()
            : job.startedAt();
    OffsetDateTime preDeploymentStart =
        resolvePreDeploymentStart(
            relevance.workflowRunStartedAt(),
            deployment != null ? deployment.createdAt() : null,
            heliosDeployment);
    heliosDeployment.setPreDeployDurationSeconds(
        calculateDurationSeconds(preDeploymentStart, deployJobStartedAt));
    heliosDeployment.setDeployDurationSeconds(
        calculateDurationSeconds(deployJobStartedAt, job.completedAt()));
    return true;
  }

  private boolean canSkipFromCache(GitHubWorkflowJobPayload payload, Relevant relevance) {
    GitHubWorkflowJobPayload.WorkflowJob job = payload.workflowJob();
    boolean deployJob = relevance.deployJobName().equals(job.name());
    boolean canRecordDeploymentId =
        deployJob
            && !relevance.deploymentIdRecorded()
            && payload.deployment() != null
            && payload.deployment().id() != null;

    if (!deployJob) {
      return !isWorkflowRunningEvent(payload.action(), job)
          || (relevance.workflowStartRecorded() && relevance.statusInProgressRecorded());
    }

    if (isJobStartedEvent(payload.action(), job)) {
      return relevance.workflowStartRecorded()
          && relevance.statusInProgressRecorded()
          && relevance.deployJobStartRecorded()
          && !canRecordDeploymentId;
    }

    if (isCompletedEvent(payload.action(), job)) {
      return relevance.deployDurationsRecorded()
          && (relevance.deploymentIdRecorded() || payload.deployment() == null);
    }

    return !canRecordDeploymentId;
  }

  private RunRelevance getCachedRelevance(Long runId) {
    RunRelevance cachedRelevance = runRelevanceCache.getIfPresent(runId);
    log.debug(
        "workflow_job run relevance cache {} for workflow run {}",
        cachedRelevance == null ? "miss" : "hit",
        runId);
    return cachedRelevance;
  }

  private RunRelevance resolveRelevance(Long runId) {
    Optional<HeliosDeploymentWorkflowJobTimingMeta> metaOpt =
        heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(runId);
    if (metaOpt.isEmpty()) {
      log.debug(
          "Skipping workflow_job timing persistence because no HeliosDeployment "
              + "matched workflow run {}",
          runId);
      return new NotDeployment();
    }

    HeliosDeploymentWorkflowJobTimingMeta meta = metaOpt.get();
    if (meta.configuredWorkflowId() == null) {
      log.debug(
          "Skipping workflow_job timing persistence because workflow run {} "
              + "is not linked to an environment deployment workflow",
          runId);
      return new WrongWorkflow();
    }

    if (meta.workflowRunWorkflowId() != null
        && !meta.workflowRunWorkflowId().equals(meta.configuredWorkflowId())) {
      log.debug(
          "Skipping workflow_job timing persistence because workflow run {} "
              + "is not linked to the configured deployment workflow {}",
          runId,
          meta.configuredWorkflowId());
      return new WrongWorkflow();
    }

    if (meta.deployJobName() == null || meta.deployJobName().isBlank()) {
      return new NoConfig(meta.heliosDeploymentId());
    }

    return new Relevant(
        meta.heliosDeploymentId(),
        meta.deployJobName(),
        meta.workflowRunStartedAt(),
        meta.createdAt(),
        meta.workflowStartedAt() != null,
        meta.status() == HeliosDeployment.Status.IN_PROGRESS,
        meta.deployJobStartedAt() != null,
        meta.deploymentId() != null,
        meta.preDeployDurationSeconds() != null && meta.deployDurationSeconds() != null);
  }

  private Relevant relevanceFromDeployment(Relevant previous, HeliosDeployment heliosDeployment) {
    return new Relevant(
        previous.heliosDeploymentId(),
        previous.deployJobName(),
        previous.workflowRunStartedAt(),
        previous.deploymentCreatedAt(),
        heliosDeployment.getWorkflowStartedAt() != null,
        heliosDeployment.getStatus() == HeliosDeployment.Status.IN_PROGRESS,
        heliosDeployment.getDeployJobStartedAt() != null,
        heliosDeployment.getDeploymentId() != null,
        heliosDeployment.getPreDeployDurationSeconds() != null
            && heliosDeployment.getDeployDurationSeconds() != null);
  }

  private boolean persistWorkflowStart(
      HeliosDeployment heliosDeployment,
      OffsetDateTime workflowRunStartedAt,
      GitHubWorkflowJobPayload payload) {
    if (!isWorkflowRunningEvent(payload.action(), payload.workflowJob())) {
      return false;
    }

    boolean changed = false;
    OffsetDateTime workflowStartedAt =
        workflowRunStartedAt != null ? workflowRunStartedAt : payload.workflowJob().startedAt();
    if (heliosDeployment.getWorkflowStartedAt() == null && workflowStartedAt != null) {
      heliosDeployment.setWorkflowStartedAt(workflowStartedAt);
      changed = true;
    }
    if (heliosDeployment.getStatus() != HeliosDeployment.Status.IN_PROGRESS) {
      heliosDeployment.setStatus(HeliosDeployment.Status.IN_PROGRESS);
      changed = true;
    }
    return changed;
  }

  private boolean isWorkflowRunningEvent(
      String action, GitHubWorkflowJobPayload.WorkflowJob job) {
    return "in_progress".equalsIgnoreCase(action)
        && "in_progress".equalsIgnoreCase(job.status())
        && job.startedAt() != null;
  }

  private boolean isJobStartedEvent(
      String action, GitHubWorkflowJobPayload.WorkflowJob job) {
    return "in_progress".equalsIgnoreCase(action)
        && "in_progress".equalsIgnoreCase(job.status())
        && job.startedAt() != null;
  }

  private boolean isCompletedEvent(
      String action, GitHubWorkflowJobPayload.WorkflowJob job) {
    return "completed".equalsIgnoreCase(action)
        && "completed".equalsIgnoreCase(job.status())
        && job.startedAt() != null
        && job.completedAt() != null;
  }

  private OffsetDateTime resolvePreDeploymentStart(
      OffsetDateTime workflowRunStartedAt,
      OffsetDateTime deploymentCreatedAt,
      HeliosDeployment heliosDeployment) {
    if (workflowRunStartedAt != null) {
      return workflowRunStartedAt;
    }

    return deploymentCreatedAt != null ? deploymentCreatedAt : heliosDeployment.getCreatedAt();
  }

  private int calculateDurationSeconds(OffsetDateTime start, OffsetDateTime end) {
    if (start == null || end == null) {
      return 0;
    }
    return Math.max(0, (int) ChronoUnit.SECONDS.between(start, end));
  }

  private sealed interface RunRelevance permits NotDeployment, WrongWorkflow, NoConfig, Relevant {}

  private record NotDeployment() implements RunRelevance {}

  private record WrongWorkflow() implements RunRelevance {}

  private record NoConfig(Long heliosDeploymentId) implements RunRelevance {}

  private record Relevant(
      Long heliosDeploymentId,
      String deployJobName,
      OffsetDateTime workflowRunStartedAt,
      OffsetDateTime deploymentCreatedAt,
      boolean workflowStartRecorded,
      boolean statusInProgressRecorded,
      boolean deployJobStartRecorded,
      boolean deploymentIdRecorded,
      boolean deployDurationsRecorded)
      implements RunRelevance {}
}
