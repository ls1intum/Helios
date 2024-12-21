package de.tum.cit.aet.helios.deployment;

import org.springframework.lang.NonNull;

public record DeployRequest(
    @NonNull Long environmentId,
    @NonNull String branchName) {
}
