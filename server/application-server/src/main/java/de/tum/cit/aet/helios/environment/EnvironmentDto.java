package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnvironmentDto(
    RepositoryInfoDto repository,
    @NonNull Long id,
    @NonNull String name,
    boolean locked,
    String url,
    String htmlUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<String> installedApps,
    String description,
    String serverUrl,
    EnvironmentDeployment latestDeployment) {

  public static record EnvironmentDeployment(
      @NonNull Long id,
      @NonNull String url,
      Deployment.State state,
      @NonNull String statusesUrl,
      @NonNull String sha,
      @NonNull String ref,
      @NonNull String task,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {

    public static EnvironmentDeployment fromDeployment(Deployment deployment) {
      return new EnvironmentDeployment(
          deployment.getId(),
          deployment.getUrl(),
          deployment.getState(),
          deployment.getStatusesUrl(),
          deployment.getSha(),
          deployment.getRef(),
          deployment.getTask(),
          deployment.getCreatedAt(),
          deployment.getUpdatedAt());
    }
  }

  public static EnvironmentDto fromEnvironment(
      Environment environment, Optional<Deployment> latestDeployment) {
    return new EnvironmentDto(
        RepositoryInfoDto.fromRepository(environment.getRepository()),
        environment.getId(),
        environment.getName(),
        environment.isLocked(),
        environment.getUrl(),
        environment.getHtmlUrl(),
        environment.getCreatedAt(),
        environment.getUpdatedAt(),
        environment.getInstalledApps(),
        environment.getDescription(),
        environment.getServerUrl(),
        latestDeployment.map(EnvironmentDeployment::fromDeployment).orElse(null));
  }

  public static EnvironmentDto fromEnvironment(Environment environment) {
    return EnvironmentDto.fromEnvironment(environment, Optional.empty());
  }
}
