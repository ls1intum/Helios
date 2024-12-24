package de.tum.cit.aet.helios.deployment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.environment.EnvironmentDTO;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDTO;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

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
    @NonNull EnvironmentDTO environment,
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
        EnvironmentDTO.fromEnvironment(deployment.getEnvironment()),
        deployment.getCreatedAt(),
        deployment.getUpdatedAt());
  }
}
