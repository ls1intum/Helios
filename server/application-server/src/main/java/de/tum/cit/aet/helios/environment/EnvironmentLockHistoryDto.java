package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.user.UserInfoDto;
import io.micrometer.common.lang.Nullable;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnvironmentLockHistoryDto(
    @NonNull Long id,
    UserInfoDto lockedBy,
    UserInfoDto unlockedBy,
    @Nullable OffsetDateTime lockedAt,
    @Nullable OffsetDateTime unlockedAt,
    EnvironmentDto environment) {

  public static EnvironmentLockHistoryDto fromEnvironmentLockHistory(
      EnvironmentLockHistory environmentLockHistory,
      EnvironmentService environmentService,
      ReleaseCandidateRepository releaseCandidateRepository) {
    Environment environment = environmentLockHistory.getEnvironment();
    return new EnvironmentLockHistoryDto(
        environmentLockHistory.getId(),
        UserInfoDto.fromUser(environmentLockHistory.getLockedBy()),
        UserInfoDto.fromUser(environmentLockHistory.getUnlockedBy()),
        environmentLockHistory.getLockedAt(),
        environmentLockHistory.getUnlockedAt(),
        EnvironmentDto.fromEnvironment(
            environment,
            environmentService.findLatestDeployment(environment),
            environment.getLatestStatus(),
            releaseCandidateRepository));
  }
}
