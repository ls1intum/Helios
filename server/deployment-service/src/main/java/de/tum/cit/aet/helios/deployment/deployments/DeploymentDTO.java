package de.tum.cit.aet.helios.deployment.deployments;

import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DeploymentDTO(
       @NonNull Long id,
       @NonNull String branchName,
       @NonNull String commitHash,
        String pullRequest,
     Long deployedByUserId,
        OffsetDateTime deployedAt) {

    public static DeploymentDTO fromDeployment(Deployment deployment) {
        return new DeploymentDTO(
                deployment.getId(),
                deployment.getBranchName(),
                deployment.getCommitHash(),
                deployment.getPullRequest(),
                deployment.getDeployedByUserId(),
                deployment.getDeployedAt());
    }
}