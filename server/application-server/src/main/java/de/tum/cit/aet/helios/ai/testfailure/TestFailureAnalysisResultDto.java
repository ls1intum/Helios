package de.tum.cit.aet.helios.ai.testfailure;

import java.util.List;

public record TestFailureAnalysisResultDto(
    String summary,
    List<String> rootCauseHypotheses,
    List<String> evidence,
    List<String> recommendedFixes,
    Double confidence,
    String provider,
    String model) {}
