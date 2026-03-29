package de.tum.cit.aet.helios.heliosdeployment;

public record DeploymentDurationEstimate(
    Double medianBuildDurationSeconds,
    Double medianDeployDurationSeconds) {}
