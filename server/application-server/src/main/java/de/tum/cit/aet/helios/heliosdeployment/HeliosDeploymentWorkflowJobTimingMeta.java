package de.tum.cit.aet.helios.heliosdeployment;

import java.time.OffsetDateTime;

/** Projection for workflow_job timing decisions without materialising deployment relationships. */
public record HeliosDeploymentWorkflowJobTimingMeta(
    Long heliosDeploymentId,
    OffsetDateTime workflowStartedAt,
    HeliosDeployment.Status status,
    OffsetDateTime deployJobStartedAt,
    Integer preDeployDurationSeconds,
    Integer deployDurationSeconds,
    Long deploymentId,
    OffsetDateTime createdAt,
    Long configuredWorkflowId,
    String deployJobName,
    OffsetDateTime workflowRunStartedAt,
    Long workflowRunWorkflowId) {}
