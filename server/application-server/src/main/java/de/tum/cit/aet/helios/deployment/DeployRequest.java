package de.tum.cit.aet.helios.deployment;

import org.springframework.lang.NonNull;

public record DeployRequest(
    @NonNull Long environmentId, String branchName, @NonNull String commitSha) {}
