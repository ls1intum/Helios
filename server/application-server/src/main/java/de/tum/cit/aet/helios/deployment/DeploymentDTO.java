package de.tum.cit.aet.helios.deployment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDTO;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DeploymentDTO(
        @NonNull Long id,
        RepositoryInfoDTO repository,
        @NonNull String url,
        Deployment.State state,
        @NonNull String statusesUrl,
        @NonNull String sha,
        @NonNull String ref,
        @NonNull String task,
        @NonNull String environment,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DeploymentDTO fromDeployment(Deployment deployment) {
        return new DeploymentDTO(
                deployment.getId(),
                RepositoryInfoDTO.fromRepository(deployment.getRepository()),
                deployment.getUrl(),
                deployment.getState(),
                deployment.getStatusesUrl(),
                deployment.getSha(),
                deployment.getRef(),
                deployment.getTask(),
                deployment.getEnvironment(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt());
    }

}
