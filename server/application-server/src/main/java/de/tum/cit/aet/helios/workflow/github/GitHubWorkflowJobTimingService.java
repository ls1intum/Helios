package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfig;
import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfigRepository;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import jakarta.transaction.Transactional;
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

  @Transactional
  public void persistDurations(GitHubWorkflowJobPayload payload) {
    if (payload == null || payload.workflowJob() == null) {
      return;
    }

    GitHubWorkflowJobPayload.WorkflowJob job = payload.workflowJob();
    if (job.runId() == null) {
      return;
    }

    Optional<HeliosDeployment> heliosDeploymentOpt =
        heliosDeploymentRepository.findByWorkflowRunId(job.runId());
    if (heliosDeploymentOpt.isEmpty()) {
      log.debug(
          "Skipping workflow_job timing persistence because no HeliosDeployment "
              + "matched workflow run {}",
          job.runId());
      return;
    }

    HeliosDeployment heliosDeployment = heliosDeploymentOpt.get();
    Environment environment = heliosDeployment.getEnvironment();
    Workflow workflow = environment != null ? environment.getDeploymentWorkflow() : null;
    if (workflow == null) {
      log.debug(
          "Skipping workflow_job timing persistence because workflow run {} "
              + "is not linked to an environment deployment workflow",
          job.runId());
      return;
    }

    Optional<DeploymentWorkflowConfig> configOpt =
        deploymentWorkflowConfigRepository.findByWorkflow(workflow);
    if (configOpt.isEmpty()) {
      return;
    }

    Optional<WorkflowRun> workflowRunOpt = workflowRunRepository.findById(job.runId());
    if (workflowRunOpt.isPresent()
        && workflowRunOpt.get().getWorkflow() != null
        && !workflowRunOpt.get().getWorkflow().getId().equals(workflow.getId())) {
      log.debug(
          "Skipping workflow_job timing persistence because workflow run {} "
              + "is not linked to the configured deployment workflow {}",
          job.runId(),
          workflow.getId());
      return;
    }

    DeploymentWorkflowConfig config = configOpt.get();
    if (config.getDeployJobName() == null
        || config.getDeployJobName().isBlank()
        || !config.getDeployJobName().equals(job.name())) {
      return;
    }

    boolean changed = false;
    if (payload.deployment() != null
        && heliosDeployment.getDeploymentId() == null
        && payload.deployment().id() != null) {
      heliosDeployment.setDeploymentId(payload.deployment().id());
      changed = true;
    }

    if (isJobStartedEvent(payload.action(), job)) {
      if (heliosDeployment.getDeployJobStartedAt() == null) {
        heliosDeployment.setDeployJobStartedAt(job.startedAt());
        changed = true;
      }
      if (changed) {
        heliosDeploymentRepository.save(heliosDeployment);
      }
      return;
    }

    if (!isCompletedEvent(payload.action(), job)) {
      if (changed) {
        heliosDeploymentRepository.save(heliosDeployment);
      }
      return;
    }

    if (heliosDeployment.getDeployJobStartedAt() == null && job.startedAt() != null) {
      heliosDeployment.setDeployJobStartedAt(job.startedAt());
      changed = true;
    }

    if (job.startedAt() == null || job.completedAt() == null) {
      if (changed) {
        heliosDeploymentRepository.save(heliosDeployment);
      }
      return;
    }

    OffsetDateTime deploymentStartedAt = heliosDeployment.getDeploymentStartedAt();
    if (deploymentStartedAt != null) {
      OffsetDateTime deployJobStartedAt =
          heliosDeployment.getDeployJobStartedAt() != null
              ? heliosDeployment.getDeployJobStartedAt()
              : job.startedAt();
      heliosDeployment.setBuildDurationSeconds(
          calculateDurationSeconds(deployJobStartedAt, deploymentStartedAt));
      heliosDeployment.setDeployDurationSeconds(
          calculateDurationSeconds(deploymentStartedAt, job.completedAt()));
    } else {
      OffsetDateTime preDeploymentStart =
          resolveLegacyPreDeploymentStart(
              workflowRunOpt.orElse(null),
              payload.deployment() != null ? payload.deployment().createdAt() : null,
              heliosDeployment);
      heliosDeployment.setBuildDurationSeconds(
          calculateDurationSeconds(preDeploymentStart, job.startedAt()));
      heliosDeployment.setDeployDurationSeconds(
          calculateDurationSeconds(job.startedAt(), job.completedAt()));
    }
    changed = true;

    if (changed) {
      heliosDeploymentRepository.save(heliosDeployment);
    }
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

  private OffsetDateTime resolveLegacyPreDeploymentStart(
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
}
