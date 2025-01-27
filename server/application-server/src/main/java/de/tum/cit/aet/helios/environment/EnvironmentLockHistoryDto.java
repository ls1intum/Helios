package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.user.User;
import io.micrometer.common.lang.Nullable;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnvironmentLockHistoryDto(
    @NonNull Long id,
    User lockedBy,
    @Nullable OffsetDateTime lockedAt,
    @Nullable OffsetDateTime unlockedAt,
    EnvironmentDto environment) {


  public static EnvironmentLockHistoryDto fromEnvironmentLockHistory(
      EnvironmentLockHistory environmentLockHistory) {
    Environment environment = environmentLockHistory.getEnvironment();
    return new EnvironmentLockHistoryDto(
        environmentLockHistory.getId(),
        environmentLockHistory.getLockedBy(),
        environmentLockHistory.getLockedAt(),
        environmentLockHistory.getUnlockedAt(),
        EnvironmentDto.fromEnvironment(environment));
  }
}