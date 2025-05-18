package de.tum.cit.aet.helios.environment.status;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record PushStatusPayload(
    @NotBlank String environment,
    @NotNull LifecycleState state,
    @NotNull Instant timestamp,
    Map<String, Object> details) {
}