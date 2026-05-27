package de.tum.cit.aet.helios.ai.testfailure;

import java.util.List;

/**
 * Structured AI output schema for test-failure analysis.
 *
 * <p>This is the typed target used by Spring AI {@code .entity(...)} conversion.
 */
record TestFailureAnalysisSchema(
    String summary,
    List<String> rootCauseHypotheses,
    List<String> evidence,
    List<String> recommendedFixes,
    Double confidence) {

  TestFailureAnalysisSchema {
    summary = summary == null ? "" : summary.trim();
    rootCauseHypotheses =
        rootCauseHypotheses == null ? List.of() : List.copyOf(rootCauseHypotheses);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    recommendedFixes = recommendedFixes == null ? List.of() : List.copyOf(recommendedFixes);
    if (confidence != null) {
      confidence = Math.max(0.0, Math.min(1.0, confidence));
    }
  }
}
