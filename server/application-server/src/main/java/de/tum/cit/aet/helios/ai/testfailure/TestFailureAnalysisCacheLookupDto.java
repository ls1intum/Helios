package de.tum.cit.aet.helios.ai.testfailure;

public record TestFailureAnalysisCacheLookupDto(
    boolean hasCachedResult,
    TestFailureAnalysisResponseDto cachedResult) {}
