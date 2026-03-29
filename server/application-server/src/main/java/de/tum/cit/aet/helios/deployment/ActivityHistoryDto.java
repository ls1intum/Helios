package de.tum.cit.aet.helios.deployment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistory;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActivityHistoryDto(
    String type,
    Long id,
    RepositoryInfoDto repository,
    String environmentName,
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
        heliosDeployment.getEnvironment().getName(),
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
    return fromDeployment(deployment, null);
  }

  public static ActivityHistoryDto fromDeployment(
      Deployment deployment, HeliosDeployment heliosDeployment) {
    // For Helios-triggered deployments, use the branch name and SHA from the HeliosDeployment if
    // available, as the GitHub Deployment's ref and sha may not reflect the actual deployed code.
    String ref = heliosDeployment != null && heliosDeployment.getBranchName() != null
        ? heliosDeployment.getBranchName()
        : deployment.getRef();
    String sha = heliosDeployment != null && heliosDeployment.getSha() != null
        ? heliosDeployment.getSha()
        : deployment.getSha();
    return new ActivityHistoryDto(
        "DEPLOYMENT",
        deployment.getId(),
        RepositoryInfoDto.fromRepository(deployment.getRepository()),
        deployment.getEnvironment().getName(),
        deployment.getState(),
        sha,
        ref,
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
        environmentLockHistory.getEnvironment().getName(),
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

  public static List<ActivityHistoryDto> sortActivityHistoryDtosByTimestampDesc(
      List<ActivityHistoryDto> activityHistoryDtos) {
    activityHistoryDtos.sort(
        (a, b) -> {
          OffsetDateTime timeA = a.timestamp();
          OffsetDateTime timeB = b.timestamp();
          if (timeA == null && timeB == null) {
            return 0;
          }
          if (timeA == null) {
            return 1;
          }
          if (timeB == null) {
            return -1;
          }
          return timeB.compareTo(timeA);
        });

    return activityHistoryDtos;
  }
}
