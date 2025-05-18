package de.tum.cit.aet.helios.deployment;

import jakarta.validation.constraints.NotNull;

/** Request object for canceling a deployment. */
public record CancelDeploymentRequest(
    @NotNull Long workflowRunId // GitHub workflow run ID to cancel
) {}
