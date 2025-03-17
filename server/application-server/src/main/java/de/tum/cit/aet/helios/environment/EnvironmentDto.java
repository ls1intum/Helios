package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion.DeploymentType;
import de.tum.cit.aet.helios.environment.status.EnvironmentStatus;
import de.tum.cit.aet.helios.environment.status.StatusCheckType;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.releasecandidate.ReleaseCandidate;
import de.tum.cit.aet.helios.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.user.UserInfoDto;
import de.tum.cit.aet.helios.workflow.WorkflowDto;
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
    OffsetDateTime lockedAt,
    Environment.Type type,
    WorkflowDto deploymentWorkflow,
    Long lockExpirationThreshold,
    Long lockReservationThreshold,
    OffsetDateTime lockWillExpireAt,
    OffsetDateTime lockReservationWillExpireAt) {

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
      LatestDeploymentUnion.State state,
      String statusesUrl,
      String sha,
      String ref,
      String task,
      String workflowRunHtmlUrl,
      String releaseCandidateName,
      String prName,
      UserInfoDto user,
      Integer pullRequestNumber,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      @NonNull DeploymentType type) {
    /** Builds an EnvironmentDeployment from a LatestDeploymentUnion. */
    public static EnvironmentDeployment fromUnion(
        LatestDeploymentUnion union, ReleaseCandidateRepository releaseCandidateRepository) {
      return new EnvironmentDeployment(
          union.getId(),
          union.getUrl(),
          union.getState(),
          union.getStatusesUrl(),
          union.getSha(),
          union.getRef(),
          union.getTask(),
          union.getWorkflowRunHtmlUrl(),
          releaseCandidateRepository
              .findByRepositoryRepositoryIdAndCommitSha(union.getRepository().id(), union.getSha())
              .map(ReleaseCandidate::getName)
              .orElse(null),
          union.getPullRequestName(),
          UserInfoDto.fromUser(union.getCreator()),
          union.getPullRequestNumber(),
          union.getCreatedAt(),
          union.getUpdatedAt(),
          union.getType());
    }
  }

  /**
   * Main factory method that takes an Environment plus a LatestDeploymentUnion (which might be
   * real, helios, or none).
   */
  public static EnvironmentDto fromEnvironment(
      Environment environment,
      LatestDeploymentUnion latestUnion,
      Optional<EnvironmentStatus> latestStatus,
      ReleaseCandidateRepository releaseCandidateRepository) {
    // If union is null or none(), we won't have a 'latestDeployment'
    EnvironmentDeployment envDeployment = null;
    if (latestUnion != null && !latestUnion.isNone()) {
      envDeployment = EnvironmentDeployment.fromUnion(latestUnion, releaseCandidateRepository);
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
        environment.getLockedAt(),
        environment.getType(),
        environment.getDeploymentWorkflow() != null
            ? WorkflowDto.fromWorkflow(environment.getDeploymentWorkflow())
            : null,
        environment.getLockExpirationThreshold(),
        environment.getLockReservationThreshold(),
        environment.getLockWillExpireAt(),
        environment.getLockReservationExpiresAt());
  }

  /** Overload if you just want to create an EnvironmentDto with no "latestDeployment" info. */
  public static EnvironmentDto fromEnvironment(Environment environment) {
    return EnvironmentDto.fromEnvironment(
        environment, LatestDeploymentUnion.none(), Optional.empty(), null);
  }
}
