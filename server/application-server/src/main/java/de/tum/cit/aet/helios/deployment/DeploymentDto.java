package de.tum.cit.aet.helios.deployment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.environment.EnvironmentDto;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DeploymentDto(
    @NonNull Long id,
    RepositoryInfoDto repository,
    @NonNull String url,
    Deployment.State state,
    @NonNull String statusesUrl,
    @NonNull String sha,
    @NonNull String ref,
    @NonNull String task,
    @NonNull EnvironmentDto environment,
    UserInfoDto user,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static DeploymentDto fromDeployment(Deployment deployment) {
    return new DeploymentDto(
        deployment.getId(),
        RepositoryInfoDto.fromRepository(deployment.getRepository()),
        deployment.getUrl(),
        deployment.getState(),
        deployment.getStatusesUrl(),
        deployment.getSha(),
        deployment.getRef(),
        deployment.getTask(),
        EnvironmentDto.fromEnvironment(deployment.getEnvironment()),
        UserInfoDto.fromUser(deployment.getCreator()),
        deployment.getCreatedAt(),
        deployment.getUpdatedAt());
  }
}
