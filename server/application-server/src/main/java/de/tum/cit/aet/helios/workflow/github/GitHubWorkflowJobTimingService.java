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
    if (!"completed".equalsIgnoreCase(payload.action())
        || !"completed".equalsIgnoreCase(job.status())
        || job.runId() == null
        || job.startedAt() == null
        || job.completedAt() == null) {
      return;
    }

    Optional<WorkflowRun> workflowRunOpt = workflowRunRepository.findById(job.runId());
    if (workflowRunOpt.isEmpty() || workflowRunOpt.get().getWorkflow() == null) {
      log.debug(
          "Skipping workflow_job timing persistence because workflow run {} is missing or has no workflow",
          job.runId());
      return;
    }

    WorkflowRun workflowRun = workflowRunOpt.get();
    Workflow workflow = workflowRun.getWorkflow();

    Optional<DeploymentWorkflowConfig> configOpt =
        deploymentWorkflowConfigRepository.findByWorkflow(workflow);
    if (configOpt.isEmpty()) {
      return;
    }

    Optional<HeliosDeployment> heliosDeploymentOpt =
        heliosDeploymentRepository.findByWorkflowRunId(job.runId());
    if (heliosDeploymentOpt.isEmpty()) {
      log.debug(
          "Skipping workflow_job timing persistence because no HeliosDeployment matched workflow run {}",
          job.runId());
      return;
    }

    HeliosDeployment heliosDeployment = heliosDeploymentOpt.get();
    Environment environment = heliosDeployment.getEnvironment();
    if (environment == null
        || environment.getDeploymentWorkflow() == null
        || !environment.getDeploymentWorkflow().getId().equals(workflow.getId())) {
      log.debug(
          "Skipping workflow_job timing persistence because workflow run {} is not linked to the configured deployment workflow {}",
          job.runId(),
          environment != null && environment.getDeploymentWorkflow() != null
              ? environment.getDeploymentWorkflow().getId()
              : null);
      return;
    }

    DeploymentWorkflowConfig config = configOpt.get();
    if (config.getDeployJobName() == null
        || config.getDeployJobName().isBlank()
        || !config.getDeployJobName().equals(job.name())) {
      return;
    }

    if (payload.deployment() != null
        && heliosDeployment.getDeploymentId() == null
        && payload.deployment().id() != null) {
      heliosDeployment.setDeploymentId(payload.deployment().id());
    }

    OffsetDateTime preDeploymentStart =
        resolvePreDeploymentStart(
            workflowRun,
            payload.deployment() != null ? payload.deployment().createdAt() : null,
            heliosDeployment);
    heliosDeployment.setBuildDurationSeconds(
        calculateDurationSeconds(preDeploymentStart, job.startedAt()));
    heliosDeployment.setDeployDurationSeconds(
        calculateDurationSeconds(job.startedAt(), job.completedAt()));
    heliosDeploymentRepository.save(heliosDeployment);
  }

  private OffsetDateTime resolvePreDeploymentStart(
      WorkflowRun workflowRun, OffsetDateTime deploymentCreatedAt, HeliosDeployment heliosDeployment) {
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
