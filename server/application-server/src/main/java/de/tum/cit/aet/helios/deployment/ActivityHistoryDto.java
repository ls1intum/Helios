package de.tum.cit.aet.helios.deployment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistory;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActivityHistoryDto(
    String type,
    Long id,
    RepositoryInfoDto repository,
    Deployment.State state,
    String sha,
    String ref,
    UserInfoDto user,
    OffsetDateTime timestamp,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static ActivityHistoryDto fromLatestDeploymentUnion(
      String type, LatestDeploymentUnion union) {
    // You could unify HeliosDeployment.Status -> Deployment.State, etc.
    return new ActivityHistoryDto(
        "DEPLOYMENT", // e.g. "LATEST_DEPLOYMENT"
        union.getId(), // union returns the ID (Helios or real)
        union.isNone()
            ? null
            : RepositoryInfoDto.fromRepository(
                union.isRealDeployment()
                    ? union.getRealDeployment().getRepository()
                    : union.getHeliosDeployment().getEnvironment().getRepository()),
        union.getState(), // mapped state
        union.getSha(), // real or helios
        union.getRef(), // branchName or real ref
        UserInfoDto.fromUser(union.getCreator()),
        union.getCreatedAt(), // we’ll consider “timestamp” = createdAt
        union.getCreatedAt(),
        union.getUpdatedAt());
  }

  public static ActivityHistoryDto fromDeployment(Deployment deployment) {
    return new ActivityHistoryDto(
        "DEPLOYMENT",
        deployment.getId(),
        RepositoryInfoDto.fromRepository(deployment.getRepository()),
        deployment.getState(),
        deployment.getSha(),
        deployment.getRef(),
        UserInfoDto.fromUser(deployment.getCreator()),
        deployment.getCreatedAt(),
        deployment.getCreatedAt(),
        deployment.getUpdatedAt());
  }

  public static ActivityHistoryDto fromEnvironmentLockHistory(
      String type, EnvironmentLockHistory environmentLockHistory) {
    return new ActivityHistoryDto(
        type,
        environmentLockHistory.getId(),
        null,
        null,
        null,
        null,
        UserInfoDto.fromUser(environmentLockHistory.getLockedBy()),
        "UNLOCK_EVENT".equals(type)
            ? environmentLockHistory.getUnlockedAt()
            : environmentLockHistory.getLockedAt(),
        null,
        null);
  }
}
