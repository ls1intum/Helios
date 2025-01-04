package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micrometer.common.lang.Nullable;
import jakarta.validation.constraints.Null;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnvironmentLockHistoryDto(
    @NonNull Long id,
    String lockedBy,
    @Nullable OffsetDateTime lockedAt,
    @Nullable OffsetDateTime unlockedAt) {


  public static EnvironmentLockHistoryDto fromEnvironmentLockHistory(
      EnvironmentLockHistory environmentLockHistory) {
    return new EnvironmentLockHistoryDto(
        environmentLockHistory.getId(),
        environmentLockHistory.getLockedBy(),
        environmentLockHistory.getLockedAt(),
        environmentLockHistory.getUnlockedAt());
  }
}