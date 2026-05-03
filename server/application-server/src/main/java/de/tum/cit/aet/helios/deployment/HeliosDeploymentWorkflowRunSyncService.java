package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowRunStateMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class HeliosDeploymentWorkflowRunSyncService {

  private final GitHubService gitHubService;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final DeploymentRepository deploymentRepository;

  @Transactional
  public boolean synchronizeTerminalStateFromWorkflowRun(
      String repositoryNameWithOwner, Long workflowRunId) throws IOException {
    if (workflowRunId == null) {
      return false;
    }

    Optional<GitHubService.WorkflowRunState> maybeWorkflowRunState =
        gitHubService.getWorkflowRunState(repositoryNameWithOwner, workflowRunId);
    if (maybeWorkflowRunState.isEmpty()) {
      return false;
    }

    Optional<HeliosDeployment.Status> maybeTerminalStatus =
        mapTerminalWorkflowRunState(maybeWorkflowRunState.get());
    if (maybeTerminalStatus.isEmpty()) {
      return false;
    }

    Optional<HeliosDeployment> maybeHeliosDeployment =
        heliosDeploymentRepository.findByWorkflowRunId(workflowRunId);
    if (maybeHeliosDeployment.isEmpty()) {
      log.warn("No Helios deployment found for workflow run {}", workflowRunId);
      return false;
    }

    updateHeliosDeployment(
        maybeHeliosDeployment.get(), maybeTerminalStatus.get(), maybeWorkflowRunState.get());
    return true;
  }

  private void updateHeliosDeployment(
      HeliosDeployment heliosDeployment,
      HeliosDeployment.Status terminalStatus,
      GitHubService.WorkflowRunState workflowRunState) {
    boolean statusChanged = heliosDeployment.getStatus() != terminalStatus;
    boolean timestampAdvanced =
        isRemoteTimestampNewer(workflowRunState.updatedAt(), heliosDeployment.getUpdatedAt());

    if (statusChanged) {
      heliosDeployment.setStatus(terminalStatus);
    }
    if (timestampAdvanced) {
      heliosDeployment.setUpdatedAt(workflowRunState.updatedAt());
    }

    if (statusChanged || timestampAdvanced) {
      heliosDeploymentRepository.save(heliosDeployment);
    }

    if (heliosDeployment.getDeploymentId() != null) {
      updateLinkedDeployment(heliosDeployment, workflowRunState.updatedAt());
    }
  }

  private void updateLinkedDeployment(
      HeliosDeployment heliosDeployment, OffsetDateTime workflowRunUpdatedAt) {
    deploymentRepository
        .findById(heliosDeployment.getDeploymentId())
        .ifPresent(deployment -> {
          Deployment.State deploymentState =
              HeliosDeployment.mapHeliosStatusToDeploymentState(heliosDeployment.getStatus());
          boolean stateChanged = deployment.getState() != deploymentState;
          boolean timestampAdvanced =
              isRemoteTimestampNewer(workflowRunUpdatedAt, deployment.getUpdatedAt());

          if (stateChanged) {
            deployment.setState(deploymentState);
          }
          if (timestampAdvanced) {
            deployment.setUpdatedAt(workflowRunUpdatedAt);
          }
          if (stateChanged || timestampAdvanced) {
            deploymentRepository.save(deployment);
          }
        });
  }

  private Optional<HeliosDeployment.Status> mapTerminalWorkflowRunState(
      GitHubService.WorkflowRunState workflowRunState) {
    WorkflowRun.Status status = GitHubWorkflowRunStateMapper.mapStatus(workflowRunState.status());
    WorkflowRun.Conclusion conclusion =
        GitHubWorkflowRunStateMapper.mapConclusion(workflowRunState.conclusion());

    if (status == null) {
      return Optional.empty();
    }

    HeliosDeployment.Status mappedStatus =
        HeliosDeployment.mapWorkflowRunStatus(status, conclusion);

    return switch (mappedStatus) {
      case WAITING, QUEUED, IN_PROGRESS -> Optional.empty();
      default -> Optional.of(mappedStatus);
    };
  }

  private boolean isRemoteTimestampNewer(OffsetDateTime remote, OffsetDateTime local) {
    return remote != null && (local == null || remote.isAfter(local));
  }
}
