package de.tum.cit.aet.helios.workflow.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfig;
import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfigRepository;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.ws.EnvironmentDeploymentWebSocketPublisher;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
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

  private final DeploymentWorkflowConfigRepository deploymentWorkflowConfigRepository;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final WorkflowRunRepository workflowRunRepository;
  private final EnvironmentDeploymentWebSocketPublisher environmentDeploymentWebSocketPublisher;
  private final Cache<Long, RunRelevance> runRelevanceCache =
      Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofHours(1)).build();

  @Transactional
  public void persistDurations(GitHubWorkflowJobPayload payload) {
    if (payload == null || payload.workflowJob() == null) {
      return;
    }

    GitHubWorkflowJobPayload.WorkflowJob job = payload.workflowJob();
    GitHubWorkflowJobPayload.Deployment deployment = payload.deployment();
    if (job.runId() == null) {
      return;
    }

    if ("queued".equalsIgnoreCase(payload.action()) && deployment == null) {
      return;
    }

    RunRelevance cachedRelevance = getCachedRelevance(job.runId());
    if (cachedRelevance instanceof NotDeployment && deployment != null) {
      runRelevanceCache.invalidate(job.runId());
    } else if (cachedRelevance != null && !(cachedRelevance instanceof Relevant)) {
      return;
    }

    ResolvedRunRelevance resolvedRelevance = resolveRelevance(job.runId());
    if (!(resolvedRelevance.relevance() instanceof Relevant)) {
      runRelevanceCache.put(job.runId(), resolvedRelevance.relevance());
      return;
    }

    runRelevanceCache.put(job.runId(), resolvedRelevance.relevance());

    HeliosDeployment heliosDeployment = resolvedRelevance.heliosDeployment();
    WorkflowRun workflowRun = resolvedRelevance.workflowRun();
    Relevant relevance = (Relevant) resolvedRelevance.relevance();

    boolean changed = persistWorkflowStart(heliosDeployment, workflowRun, payload);

    if (!relevance.deployJobName().equals(job.name())) {
      if (changed) {
        saveAndPublish(heliosDeployment);
      }
      return;
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
      if (changed) {
        saveAndPublish(heliosDeployment);
      }
      return;
    }

    if (!isCompletedEvent(payload.action(), job)) {
      if (changed) {
        saveAndPublish(heliosDeployment);
      }
      return;
    }

    if (heliosDeployment.getDeployJobStartedAt() == null && job.startedAt() != null) {
      heliosDeployment.setDeployJobStartedAt(job.startedAt());
      changed = true;
    }

    if (job.startedAt() == null || job.completedAt() == null) {
      if (changed) {
        saveAndPublish(heliosDeployment);
      }
      return;
    }

    OffsetDateTime deployJobStartedAt =
        heliosDeployment.getDeployJobStartedAt() != null
            ? heliosDeployment.getDeployJobStartedAt()
            : job.startedAt();
    OffsetDateTime preDeploymentStart =
        resolvePreDeploymentStart(
            workflowRun,
            deployment != null ? deployment.createdAt() : null,
            heliosDeployment);
    heliosDeployment.setPreDeployDurationSeconds(
        calculateDurationSeconds(preDeploymentStart, deployJobStartedAt));
    heliosDeployment.setDeployDurationSeconds(
        calculateDurationSeconds(deployJobStartedAt, job.completedAt()));
    changed = true;

    if (changed) {
      saveAndPublish(heliosDeployment);
    }
  }

  private void saveAndPublish(HeliosDeployment heliosDeployment) {
    heliosDeploymentRepository.save(heliosDeployment);
    environmentDeploymentWebSocketPublisher.publishAfterCommit(heliosDeployment);
  }

  private RunRelevance getCachedRelevance(Long runId) {
    RunRelevance cachedRelevance = runRelevanceCache.getIfPresent(runId);
    log.debug(
        "workflow_job run relevance cache {} for workflow run {}",
        cachedRelevance == null ? "miss" : "hit",
        runId);
    return cachedRelevance;
  }

  private ResolvedRunRelevance resolveRelevance(Long runId) {
    Optional<HeliosDeployment> heliosDeploymentOpt =
        heliosDeploymentRepository.findByWorkflowRunId(runId);
    if (heliosDeploymentOpt.isEmpty()) {
      log.debug(
          "Skipping workflow_job timing persistence because no HeliosDeployment "
              + "matched workflow run {}",
          runId);
      return new ResolvedRunRelevance(new NotDeployment(), null, null);
    }

    HeliosDeployment heliosDeployment = heliosDeploymentOpt.get();
    Environment environment = heliosDeployment.getEnvironment();
    Workflow workflow = environment != null ? environment.getDeploymentWorkflow() : null;
    if (workflow == null) {
      log.debug(
          "Skipping workflow_job timing persistence because workflow run {} "
              + "is not linked to an environment deployment workflow",
          runId);
      return new ResolvedRunRelevance(new WrongWorkflow(), heliosDeployment, null);
    }

    Optional<WorkflowRun> workflowRunOpt = workflowRunRepository.findById(runId);
    WorkflowRun workflowRun = workflowRunOpt.orElse(null);
    if (workflowRun != null
        && workflowRun.getWorkflow() != null
        && !workflowRun.getWorkflow().getId().equals(workflow.getId())) {
      log.debug(
          "Skipping workflow_job timing persistence because workflow run {} "
              + "is not linked to the configured deployment workflow {}",
          runId,
          workflow.getId());
      return new ResolvedRunRelevance(new WrongWorkflow(), heliosDeployment, workflowRun);
    }

    Optional<DeploymentWorkflowConfig> configOpt =
        deploymentWorkflowConfigRepository.findByWorkflow(workflow);
    if (configOpt.isEmpty()) {
      return new ResolvedRunRelevance(
          new NoConfig(heliosDeployment.getId()), heliosDeployment, workflowRun);
    }

    DeploymentWorkflowConfig config = configOpt.get();
    if (config.getDeployJobName() == null || config.getDeployJobName().isBlank()) {
      return new ResolvedRunRelevance(
          new NoConfig(heliosDeployment.getId()), heliosDeployment, workflowRun);
    }

    return new ResolvedRunRelevance(
        new Relevant(heliosDeployment.getId(), config.getDeployJobName()),
        heliosDeployment,
        workflowRun);
  }

  private boolean persistWorkflowStart(
      HeliosDeployment heliosDeployment,
      WorkflowRun workflowRun,
      GitHubWorkflowJobPayload payload) {
    if (!isWorkflowRunningEvent(payload.action(), payload.workflowJob())) {
      return false;
    }

    boolean changed = false;
    OffsetDateTime workflowStartedAt =
        workflowRun != null && workflowRun.getRunStartedAt() != null
            ? workflowRun.getRunStartedAt()
            : payload.workflowJob().startedAt();
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
      WorkflowRun workflowRun,
      OffsetDateTime deploymentCreatedAt,
      HeliosDeployment heliosDeployment) {
    if (workflowRun != null && workflowRun.getRunStartedAt() != null) {
      return workflowRun.getRunStartedAt();
    }

    return deploymentCreatedAt != null ? deploymentCreatedAt : heliosDeployment.getCreatedAt();
  }

  private int calculateDurationSeconds(OffsetDateTime start, OffsetDateTime end) {
    if (start == null || end == null) {
      return 0;
    }
    return Math.max(0, (int) ChronoUnit.SECONDS.between(start, end));
  }

  private record ResolvedRunRelevance(
      RunRelevance relevance, HeliosDeployment heliosDeployment, WorkflowRun workflowRun) {}

  private sealed interface RunRelevance permits NotDeployment, WrongWorkflow, NoConfig, Relevant {}

  private record NotDeployment() implements RunRelevance {}

  private record WrongWorkflow() implements RunRelevance {}

  private record NoConfig(Long heliosDeploymentId) implements RunRelevance {}

  private record Relevant(Long heliosDeploymentId, String deployJobName) implements RunRelevance {}
}
