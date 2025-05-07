package de.tum.cit.aet.helios.environment.status;

import java.time.Instant;
import java.util.Map;

public record PushStatusPayload(
    String environment,
    String state,
    Instant timestamp,
    Map<String, Object> details) {
}