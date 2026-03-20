package de.tum.cit.aet.helios.tests;

import org.springframework.lang.NonNull;

public record TestFlakinessScoreDto(
    @NonNull String testName,
    @NonNull String className,
    @NonNull String testSuiteName,
    double flakinessScore,
    double defaultBranchFailureRate,
    double combinedFailureRate) {
}
