package de.tum.cit.aet.helios.deployment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistory;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.user.User;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActivityHistoryDto(
    String type,
    Long id,
    RepositoryInfoDto repository,
    Deployment.State state,
    String sha,
    String ref,
    User lockedBy,
    OffsetDateTime timestamp,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
  
  public static ActivityHistoryDto fromDeployment(Deployment deployment) {
    return new ActivityHistoryDto(
        "DEPLOYMENT",
        deployment.getId(),
        RepositoryInfoDto.fromRepository(deployment.getRepository()),
        deployment.getState(),
        deployment.getSha(),
        deployment.getRef(),
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
        environmentLockHistory.getLockedBy(),
        "UNLOCK_EVENT".equals(type) 
            ? environmentLockHistory.getUnlockedAt() 
            : environmentLockHistory.getLockedAt(),
        null,
        null);
  }
}
