package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.environment.status.EnvironmentStatus;
import de.tum.cit.aet.helios.environment.status.StatusCheckType;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.lang.NonNull;

public record EnvironmentDto(
    RepositoryInfoDto repository,
    @NonNull Long id,
    @NonNull String name,
    boolean locked,
    String url,
    String htmlUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean enabled,
    List<String> installedApps,
    String description,
    String serverUrl,
    StatusCheckType statusCheckType,
    String statusUrl,
    EnvironmentDeployment latestDeployment,
    EnvironmentStatusDto latestStatus,
    String lockedBy,
    OffsetDateTime lockedAt) {

  public static record EnvironmentStatusDto(
      @NonNull Long id,
      @NonNull Boolean success,
      Integer statusCode,
      @NonNull Instant checkedAt,
      StatusCheckType checkType,
      Map<String, Object> metadata) {
    public static EnvironmentStatusDto fromEnvironment(EnvironmentStatus environment) {
      return new EnvironmentStatusDto(
          environment.getId(),
          environment.isSuccess(),
          environment.getStatusCode(),
          environment.getCheckTimestamp(),
          environment.getCheckType(),
          environment.getMetadata());
    }
  }

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
      Environment environment, Optional<Deployment> latestDeployment,
      Optional<EnvironmentStatus> latestStatus) {
    return new EnvironmentDto(
        RepositoryInfoDto.fromRepository(environment.getRepository()),
        environment.getId(),
        environment.getName(),
        environment.isLocked(),
        environment.getUrl(),
        environment.getHtmlUrl(),
        environment.getCreatedAt(),
        environment.getUpdatedAt(),
        environment.isEnabled(),
        environment.getInstalledApps(),
        environment.getDescription(),
        environment.getServerUrl(),
        environment.getStatusCheckType(),
        environment.getStatusUrl(),
        latestDeployment.map(EnvironmentDeployment::fromDeployment).orElse(null),
        latestStatus.map(EnvironmentStatusDto::fromEnvironment).orElse(null),
        environment.getLockedBy(),
        environment.getLockedAt());
  }

  public static EnvironmentDto fromEnvironment(Environment environment) {
    return EnvironmentDto.fromEnvironment(environment, Optional.empty(), Optional.empty());
  }
}
