package de.tum.cit.aet.helios.heliosdeployment;

public record DeploymentDurationEstimate(
    Double medianPreDeployDurationSeconds,
    Double medianDeployDurationSeconds) {}
