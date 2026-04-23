package de.tum.cit.aet.helios.ai.testfailure;

import java.time.OffsetDateTime;

public record TestFailureAnalysisResponseDto(
    Long repositoryId,
    TestFailureAnalysisStatus status,
    TestFailureAnalysisResultDto result,
    String errorMessage,
    OffsetDateTime analyzedAt,
    Long durationMs,
    boolean cacheHit) {}
