package de.tum.cit.aet.helios.ai.testfailure;

public record TestFailureAnalysisUsageDto(
    boolean rateLimitEnabled,
    Integer dailyUsed,
    Integer dailyLimit,
    Integer burstUsed,
    Integer burstLimit,
    Long burstWindowSeconds) {}
