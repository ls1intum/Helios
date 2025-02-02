package de.tum.cit.aet.helios.environment.status;

import java.util.Map;

public record StatusCheckResult(
    boolean success,
    int httpStatusCode,
    Map<String, Object> metadata) {
}