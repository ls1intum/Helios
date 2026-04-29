package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion.State;
import de.tum.cit.aet.helios.environment.DeploymentTimerDto.DeploymentTimerStepDto;
import de.tum.cit.aet.helios.environment.DeploymentTimerDto.HeaderMode;
import de.tum.cit.aet.helios.environment.DeploymentTimerDto.StepKey;
import de.tum.cit.aet.helios.environment.DeploymentTimerDto.StepMode;
import de.tum.cit.aet.helios.environment.DeploymentTimerDto.StepStatus;
import de.tum.cit.aet.helios.heliosdeployment.DeploymentDurationEstimate;
import java.time.OffsetDateTime;
import java.util.List;

public final class DeploymentTimerMapper {

  private static final int PULL_REQUEST_PRE_DEPLOY_DEFAULT_SECONDS = 2 * 60;
  private static final int BRANCH_PRE_DEPLOY_DEFAULT_SECONDS = 11 * 60;
  private static final int DEPLOY_DEFAULT_SECONDS = 4 * 60;
  private static final int MIN_HISTORICAL_ESTIMATE_SECONDS = 30;

  private DeploymentTimerMapper() {}

  public static DeploymentTimerDto fromUnion(
      LatestDeploymentUnion union, DeploymentDurationEstimate estimate) {
    int preDeployEstimate = resolvePreDeployEstimateSeconds(union, estimate);
    int deployEstimate = resolveDeployEstimateSeconds(estimate);
    OffsetDateTime workflowStartedAt = union.getWorkflowStartedAt();
    OffsetDateTime deployJobStartedAt = union.getDeployJobStartedAt();
    OffsetDateTime totalStartedAt =
        workflowStartedAt != null ? workflowStartedAt : union.getCreatedAt();
    State state = union.getState();

    if (isUnknownState(state)) {
      return new DeploymentTimerDto(
          "Deployment Status Unknown",
          HeaderMode.NONE,
          null,
          null,
          null,
          false,
          List.of(
              step(StepKey.PRE_DEPLOYMENT, StepStatus.UNKNOWN, StepMode.NONE, null, null, null),
              step(StepKey.DEPLOYMENT, StepStatus.UNKNOWN, StepMode.NONE, null, null, null)));
    }

    if (isSuccessState(state)) {
      return new DeploymentTimerDto(
          "Deployment Completed",
          HeaderMode.DURATION,
          totalStartedAt,
          union.getUpdatedAt(),
          null,
          false,
          List.of(
              step(
                  StepKey.PRE_DEPLOYMENT,
                  StepStatus.COMPLETED,
                  StepMode.COMPLETED,
                  workflowStartedAt,
                  deployJobStartedAt,
                  preDeployEstimate),
              step(
                  StepKey.DEPLOYMENT,
                  StepStatus.COMPLETED,
                  StepMode.COMPLETED,
                  deployJobStartedAt,
                  union.getUpdatedAt(),
                  deployEstimate)));
    }

    if (isFailureState(state)) {
      boolean failedInDeployment = deployJobStartedAt != null;
      return new DeploymentTimerDto(
          state == State.CANCELLED ? "Deployment Cancelled" : "Deployment Failed",
          HeaderMode.DURATION,
          totalStartedAt,
          union.getUpdatedAt(),
          null,
          false,
          List.of(
              step(
                  StepKey.PRE_DEPLOYMENT,
                  failedInDeployment ? StepStatus.COMPLETED : StepStatus.ERROR,
                  failedInDeployment ? StepMode.COMPLETED : StepMode.FAILED,
                  workflowStartedAt,
                  failedInDeployment ? deployJobStartedAt : union.getUpdatedAt(),
                  preDeployEstimate),
              step(
                  StepKey.DEPLOYMENT,
                  failedInDeployment ? StepStatus.ERROR : StepStatus.UNKNOWN,
                  failedInDeployment ? StepMode.FAILED : StepMode.NONE,
                  deployJobStartedAt,
                  failedInDeployment ? union.getUpdatedAt() : null,
                  deployEstimate)));
    }

    if (deployJobStartedAt != null) {
      return new DeploymentTimerDto(
          "Deployment in Progress",
          HeaderMode.REMAINING,
          deployJobStartedAt,
          null,
          deployEstimate,
          false,
          List.of(
              step(
                  StepKey.PRE_DEPLOYMENT,
                  StepStatus.COMPLETED,
                  StepMode.COMPLETED,
                  workflowStartedAt,
                  deployJobStartedAt,
                  preDeployEstimate),
              step(
                  StepKey.DEPLOYMENT,
                  StepStatus.ACTIVE,
                  StepMode.REMAINING,
                  deployJobStartedAt,
                  null,
                  deployEstimate)));
    }

    boolean workflowStarted = workflowStartedAt != null;
    boolean queued = state == State.QUEUED;
    String title = resolveActiveTitle(state, workflowStarted);
    HeaderMode headerMode = workflowStarted ? HeaderMode.REMAINING : HeaderMode.ESTIMATED;
    StepMode preDeployMode = workflowStarted ? StepMode.REMAINING : StepMode.ESTIMATED;

    return new DeploymentTimerDto(
        title,
        headerMode,
        workflowStartedAt,
        null,
        preDeployEstimate + deployEstimate,
        queued,
        List.of(
            step(
                StepKey.PRE_DEPLOYMENT,
                StepStatus.ACTIVE,
                preDeployMode,
                workflowStartedAt,
                null,
                preDeployEstimate),
            step(
                StepKey.DEPLOYMENT,
                StepStatus.UPCOMING,
                StepMode.ESTIMATED,
                null,
                null,
                deployEstimate)));
  }

  private static String resolveActiveTitle(State state, boolean workflowStarted) {
    if (state == State.QUEUED) {
      return "Deployment Queued";
    }
    if (workflowStarted || state == State.IN_PROGRESS) {
      return "Deployment in Progress";
    }
    return "Deployment Requested";
  }

  private static boolean isUnknownState(State state) {
    return state == State.UNKNOWN || state == State.INACTIVE || state == null;
  }

  private static boolean isSuccessState(State state) {
    return state == State.SUCCESS;
  }

  private static boolean isFailureState(State state) {
    return state == State.ERROR
        || state == State.FAILURE
        || state == State.CANCELLED;
  }

  private static DeploymentTimerStepDto step(
      StepKey key,
      StepStatus status,
      StepMode mode,
      OffsetDateTime startedAt,
      OffsetDateTime endedAt,
      Integer estimateSeconds) {
    return new DeploymentTimerStepDto(key, label(key), status, mode, startedAt, endedAt,
        estimateSeconds);
  }

  private static String label(StepKey key) {
    return switch (key) {
      case PRE_DEPLOYMENT -> "PRE-DEPLOYMENT";
      case DEPLOYMENT -> "DEPLOYMENT";
    };
  }

  private static int resolvePreDeployEstimateSeconds(
      LatestDeploymentUnion union, DeploymentDurationEstimate estimate) {
    Integer historicalEstimate =
        estimate != null && estimate.medianPreDeployDurationSeconds() != null
            ? (int) Math.round(estimate.medianPreDeployDurationSeconds())
            : null;
    if (historicalEstimate != null && historicalEstimate > 0) {
      return Math.max(historicalEstimate, MIN_HISTORICAL_ESTIMATE_SECONDS);
    }
    return union.getPullRequestNumber() != null || union.getPullRequestName() != null
        ? PULL_REQUEST_PRE_DEPLOY_DEFAULT_SECONDS
        : BRANCH_PRE_DEPLOY_DEFAULT_SECONDS;
  }

  private static int resolveDeployEstimateSeconds(DeploymentDurationEstimate estimate) {
    Integer historicalEstimate =
        estimate != null && estimate.medianDeployDurationSeconds() != null
            ? (int) Math.round(estimate.medianDeployDurationSeconds())
            : null;
    if (historicalEstimate != null && historicalEstimate > 0) {
      return Math.max(historicalEstimate, MIN_HISTORICAL_ESTIMATE_SECONDS);
    }
    return DEPLOY_DEFAULT_SECONDS;
  }
}
