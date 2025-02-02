package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.environment.status.EnvironmentStatus;
import de.tum.cit.aet.helios.environment.status.StatusCheckType;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.user.UserInfoDto;
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
    UserInfoDto lockedBy,
    OffsetDateTime lockedAt) {

  public static record EnvironmentStatusDto(
      @NonNull Long id,
      @NonNull Boolean success,
      @NonNull Integer httpStatusCode,
      @NonNull Instant checkedAt,
      @NonNull StatusCheckType checkType,
      Map<String, Object> metadata) {
    public static EnvironmentStatusDto fromEnvironmentStatus(EnvironmentStatus environment) {
      return new EnvironmentStatusDto(
          environment.getId(),
          environment.isSuccess(),
          environment.getHttpStatusCode(),
          environment.getCheckTimestamp(),
          environment.getCheckType(),
          environment.getMetadata());
    }
  }

  /** This is the DTO for the "latestDeployment" portion inside EnvironmentDto. */
  public static record EnvironmentDeployment(
      @NonNull Long id,
      String url,
      Deployment.State state,
      String statusesUrl,
      String sha,
      String ref,
      String task,
      UserInfoDto user,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    /** Builds an EnvironmentDeployment from a LatestDeploymentUnion. */
    public static EnvironmentDeployment fromUnion(LatestDeploymentUnion union) {
      return new EnvironmentDeployment(
          union.getId(),
          union.getUrl(),
          union.getState(),
          union.getStatusesUrl(),
          union.getSha(),
          union.getRef(),
          union.getTask(),
          UserInfoDto.fromUser(union.getCreator()),
          union.getCreatedAt(),
          union.getUpdatedAt());
    }
  }

  /**
   * Main factory method that takes an Environment plus a LatestDeploymentUnion (which might be
   * real, helios, or none).
   */
  public static EnvironmentDto fromEnvironment(
      Environment environment,
      LatestDeploymentUnion latestUnion,
      Optional<EnvironmentStatus> latestStatus) {
    // If union is null or none(), we won't have a 'latestDeployment'
    EnvironmentDeployment envDeployment = null;
    if (latestUnion != null && !latestUnion.isNone()) {
      envDeployment = EnvironmentDeployment.fromUnion(latestUnion);
    }

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
        envDeployment,
        latestStatus.map(EnvironmentStatusDto::fromEnvironmentStatus).orElse(null),
        UserInfoDto.fromUser(environment.getLockedBy()),
        environment.getLockedAt());
  }

  /** Overload if you just want to create an EnvironmentDto with no "latestDeployment" info. */
  public static EnvironmentDto fromEnvironment(Environment environment) {
    return EnvironmentDto.fromEnvironment(
        environment, LatestDeploymentUnion.none(), Optional.empty());
  }
}
