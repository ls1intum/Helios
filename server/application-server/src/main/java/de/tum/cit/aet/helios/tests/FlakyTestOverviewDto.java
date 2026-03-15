package de.tum.cit.aet.helios.tests;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public record FlakyTestOverviewDto(
    @NonNull FlakyTestSummary summary,
    @NonNull List<FlakyTestDto> flakyTests) {

  public record FlakyTestDto(
      @NonNull String testName,
      @NonNull String className,
      @NonNull String testSuiteName,
      double flakinessScore,
      double defaultBranchFailureRate,
      double combinedFailureRate,
      int totalRuns,
      int failedRuns,
      @NonNull OffsetDateTime lastUpdated) {

    public static FlakyTestDto from(
        TestCaseStatistics stat, TestCaseStatisticsService.FlakinessInfo info) {
      return new FlakyTestDto(
          stat.getTestName(), stat.getClassName(), stat.getTestSuiteName(),
          info.flakinessScore(), info.defaultBranchFailureRate(), info.combinedFailureRate(),
          stat.getTotalRuns(), stat.getFailedRuns(), stat.getLastUpdated());
    }
  }

  public record FlakyTestSummary(
      int totalTrackedTests,
      int flakyTestCount,
      int highFlakinessCount,
      int mediumFlakinessCount,
      int lowFlakinessCount) {

    public static FlakyTestSummary buildSummary(
        int totalTrackedTests, List<FlakyTestDto> flakyTests) {
      int highCount = 0;
      int mediumCount = 0;
      int lowCount = 0;

      for (FlakyTestDto t : flakyTests) {
        if (t.flakinessScore() > 70) {
          highCount++;
        } else if (t.flakinessScore() > 30) {
          mediumCount++;
        } else {
          lowCount++;
        }
      }

      return new FlakyTestSummary(
          totalTrackedTests, flakyTests.size(), highCount, mediumCount, lowCount);
    }
  }

}
