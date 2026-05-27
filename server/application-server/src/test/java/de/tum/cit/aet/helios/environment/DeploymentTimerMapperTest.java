package de.tum.cit.aet.helios.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.environment.DeploymentTimerDto.HeaderMode;
import de.tum.cit.aet.helios.environment.DeploymentTimerDto.StepMode;
import de.tum.cit.aet.helios.environment.DeploymentTimerDto.StepStatus;
import de.tum.cit.aet.helios.heliosdeployment.DeploymentDurationEstimate;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class DeploymentTimerMapperTest {

  private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-04-23T10:00:00Z");
  private static final OffsetDateTime WORKFLOW_STARTED_AT =
      OffsetDateTime.parse("2026-04-23T10:01:00Z");
  private static final OffsetDateTime DEPLOY_JOB_STARTED_AT =
      OffsetDateTime.parse("2026-04-23T10:03:00Z");
  private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-04-23T10:07:00Z");

  @Test
  void requestedBeforeWorkflowStartShowsPreDeploymentAsEstimated() {
    DeploymentTimerDto timer =
        DeploymentTimerMapper.fromUnion(
            LatestDeploymentUnion.heliosDeployment(deployment(HeliosDeployment.Status.WAITING)),
            estimate());

    assertEquals("Deployment Requested", timer.title());
    assertEquals(HeaderMode.ESTIMATED, timer.headerMode());
    assertEquals(Integer.valueOf(360), timer.headerEstimateSeconds());
    assertFalse(timer.showQueuedMessage());
    assertEquals(StepStatus.ACTIVE, timer.steps().get(0).status());
    assertEquals(StepMode.ESTIMATED, timer.steps().get(0).mode());
    assertEquals(StepStatus.UPCOMING, timer.steps().get(1).status());
  }

  @Test
  void queuedBeforeWorkflowStartShowsQueuedMessage() {
    DeploymentTimerDto timer =
        DeploymentTimerMapper.fromUnion(
            LatestDeploymentUnion.heliosDeployment(deployment(HeliosDeployment.Status.QUEUED)),
            estimate());

    assertEquals("Deployment Queued", timer.title());
    assertTrue(timer.showQueuedMessage());
    assertEquals(HeaderMode.ESTIMATED, timer.headerMode());
  }

  @Test
  void workflowStartedShowsPreDeploymentRemaining() {
    HeliosDeployment deployment = deployment(HeliosDeployment.Status.IN_PROGRESS);
    deployment.setWorkflowStartedAt(WORKFLOW_STARTED_AT);

    DeploymentTimerDto timer =
        DeploymentTimerMapper.fromUnion(
            LatestDeploymentUnion.heliosDeployment(deployment), estimate());

    assertEquals("Deployment in Progress", timer.title());
    assertEquals(HeaderMode.REMAINING, timer.headerMode());
    assertEquals(WORKFLOW_STARTED_AT, timer.headerStartedAt());
    assertEquals(StepStatus.ACTIVE, timer.steps().get(0).status());
    assertEquals(StepMode.REMAINING, timer.steps().get(0).mode());
    assertEquals(StepStatus.UPCOMING, timer.steps().get(1).status());
  }

  @Test
  void deployJobStartedShowsDeploymentRemaining() {
    HeliosDeployment deployment = deployment(HeliosDeployment.Status.IN_PROGRESS);
    deployment.setWorkflowStartedAt(WORKFLOW_STARTED_AT);
    deployment.setDeployJobStartedAt(DEPLOY_JOB_STARTED_AT);

    DeploymentTimerDto timer =
        DeploymentTimerMapper.fromUnion(
            LatestDeploymentUnion.heliosDeployment(deployment), estimate());

    assertEquals(HeaderMode.REMAINING, timer.headerMode());
    assertEquals(DEPLOY_JOB_STARTED_AT, timer.headerStartedAt());
    assertEquals(StepStatus.COMPLETED, timer.steps().get(0).status());
    assertEquals(StepStatus.ACTIVE, timer.steps().get(1).status());
    assertEquals(StepMode.REMAINING, timer.steps().get(1).mode());
  }

  @Test
  void successShowsCompletedDuration() {
    HeliosDeployment deployment = deployment(HeliosDeployment.Status.DEPLOYMENT_SUCCESS);
    deployment.setWorkflowStartedAt(WORKFLOW_STARTED_AT);
    deployment.setDeployJobStartedAt(DEPLOY_JOB_STARTED_AT);
    deployment.setUpdatedAt(UPDATED_AT);

    DeploymentTimerDto timer =
        DeploymentTimerMapper.fromUnion(
            LatestDeploymentUnion.heliosDeployment(deployment), estimate());

    assertEquals("Deployment Completed", timer.title());
    assertEquals(HeaderMode.DURATION, timer.headerMode());
    assertEquals(WORKFLOW_STARTED_AT, timer.headerStartedAt());
    assertEquals(UPDATED_AT, timer.headerEndedAt());
    assertEquals(StepStatus.COMPLETED, timer.steps().get(0).status());
    assertEquals(StepStatus.COMPLETED, timer.steps().get(1).status());
  }

  @Test
  void failureMarksLastKnownStepAsError() {
    HeliosDeployment preDeployFailure = deployment(HeliosDeployment.Status.FAILED);
    preDeployFailure.setWorkflowStartedAt(WORKFLOW_STARTED_AT);
    preDeployFailure.setUpdatedAt(UPDATED_AT);

    DeploymentTimerDto preDeployTimer =
        DeploymentTimerMapper.fromUnion(
            LatestDeploymentUnion.heliosDeployment(preDeployFailure), estimate());

    assertEquals(StepStatus.ERROR, preDeployTimer.steps().get(0).status());
    assertEquals(StepStatus.UNKNOWN, preDeployTimer.steps().get(1).status());

    HeliosDeployment deployFailure = deployment(HeliosDeployment.Status.FAILED);
    deployFailure.setWorkflowStartedAt(WORKFLOW_STARTED_AT);
    deployFailure.setDeployJobStartedAt(DEPLOY_JOB_STARTED_AT);
    deployFailure.setUpdatedAt(UPDATED_AT);

    DeploymentTimerDto deployTimer =
        DeploymentTimerMapper.fromUnion(
            LatestDeploymentUnion.heliosDeployment(deployFailure), estimate());

    assertEquals(StepStatus.COMPLETED, deployTimer.steps().get(0).status());
    assertEquals(StepStatus.ERROR, deployTimer.steps().get(1).status());
  }

  @Test
  void unknownStateDoesNotShowCountdown() {
    DeploymentTimerDto timer =
        DeploymentTimerMapper.fromUnion(
            LatestDeploymentUnion.heliosDeployment(deployment(HeliosDeployment.Status.UNKNOWN)),
            estimate());

    assertEquals("Deployment Status Unknown", timer.title());
    assertEquals(HeaderMode.NONE, timer.headerMode());
    assertEquals(StepStatus.UNKNOWN, timer.steps().get(0).status());
    assertEquals(StepMode.NONE, timer.steps().get(0).mode());
  }

  private HeliosDeployment deployment(HeliosDeployment.Status status) {
    HeliosDeployment deployment = new HeliosDeployment();
    deployment.setId(1L);
    deployment.setStatus(status);
    deployment.setCreatedAt(CREATED_AT);
    deployment.setUpdatedAt(CREATED_AT);
    return deployment;
  }

  private DeploymentDurationEstimate estimate() {
    return new DeploymentDurationEstimate(120.0, 240.0);
  }
}
