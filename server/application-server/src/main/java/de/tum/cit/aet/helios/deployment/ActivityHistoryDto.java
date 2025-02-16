package de.tum.cit.aet.helios.deployment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistory;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
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
    UserInfoDto user, // lockedBy or DeployedBy
    UserInfoDto user2, // unlockedBy
    OffsetDateTime timestamp,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static ActivityHistoryDto fromHeliosDeployment(HeliosDeployment heliosDeployment) {
    return new ActivityHistoryDto(
        "DEPLOYMENT",
        heliosDeployment.getId(),
        RepositoryInfoDto.fromRepository(heliosDeployment.getEnvironment().getRepository()),
        HeliosDeployment.mapHeliosStatusToDeploymentState(heliosDeployment.getStatus()),
        heliosDeployment.getSha(),
        heliosDeployment.getBranchName(),
        UserInfoDto.fromUser(heliosDeployment.getCreator()),
        null,
        heliosDeployment.getCreatedAt(),
        heliosDeployment.getCreatedAt(),
        heliosDeployment.getUpdatedAt());
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
        null,
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
            ? UserInfoDto.fromUser(environmentLockHistory.getUnlockedBy())
            : null,
        "UNLOCK_EVENT".equals(type)
            ? environmentLockHistory.getUnlockedAt()
            : environmentLockHistory.getLockedAt(),
        null,
        null);
  }
}
