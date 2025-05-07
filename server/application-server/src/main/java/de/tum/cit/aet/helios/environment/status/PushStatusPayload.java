package de.tum.cit.aet.helios.environment.status;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record PushStatusPayload(
    @NotBlank String environment,
    @NotBlank String state,
    @NotNull Instant timestamp,
    Map<String, Object> details) {
}