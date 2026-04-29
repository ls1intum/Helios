package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public record DeploymentTimerDto(
    @NonNull String title,
    @NonNull HeaderMode headerMode,
    OffsetDateTime headerStartedAt,
    OffsetDateTime headerEndedAt,
    Integer headerEstimateSeconds,
    boolean showQueuedMessage,
    @NonNull List<DeploymentTimerStepDto> steps) {

  public enum HeaderMode {
    NONE,
    DURATION,
    ESTIMATED,
    REMAINING
  }

  public record DeploymentTimerStepDto(
      @NonNull StepKey key,
      @NonNull String label,
      @NonNull StepStatus status,
      @NonNull StepMode mode,
      OffsetDateTime startedAt,
      OffsetDateTime endedAt,
      Integer estimateSeconds) {}

  public enum StepKey {
    PRE_DEPLOYMENT,
    DEPLOYMENT
  }

  @Schema(
      type = "string",
      allowableValues = {"completed", "active", "error", "upcoming", "unknown"})
  public enum StepStatus {
    COMPLETED("completed"),
    ACTIVE("active"),
    ERROR("error"),
    UPCOMING("upcoming"),
    UNKNOWN("unknown");

    private final String value;

    StepStatus(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }
  }

  public enum StepMode {
    NONE,
    COMPLETED,
    FAILED,
    ESTIMATED,
    REMAINING
  }
}
