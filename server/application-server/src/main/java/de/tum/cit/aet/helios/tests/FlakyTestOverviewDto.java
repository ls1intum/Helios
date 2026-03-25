package de.tum.cit.aet.helios.tests;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public record FlakyTestOverviewDto(
    @NonNull FlakyTestSummary summary,
    @NonNull List<FlakyTestDto> flakyTests,
    long filteredCount) {

  public record FlakyTestDto(
      @NonNull String testName,
      @NonNull String className,
      @NonNull String testSuiteName,
      double flakinessScore,
      double defaultBranchFailureRate,
      double combinedFailureRate,
      @NonNull OffsetDateTime lastUpdated) {

    public static FlakyTestDto from(TestCaseFlakiness flakiness) {
      return new FlakyTestDto(
          flakiness.getTestName(),
          flakiness.getClassName(),
          flakiness.getTestSuiteName(),
          flakiness.getFlakinessScore(),
          flakiness.getDefaultBranchFailureRate(),
          flakiness.getCombinedFailureRate(),
          flakiness.getLastUpdated());
    }
  }

  public record FlakyTestSummary(
      int totalTrackedTests,
      int flakyTestCount,
      int highFlakinessCount,
      int mediumFlakinessCount,
      int lowFlakinessCount) {}
}
