package de.tum.cit.aet.helios.tests;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestSuiteSummaryDto {
  private Integer tests;
  private Integer failures;
  private Integer errors;
  private Integer skipped;
  private Double time;
  private Boolean hasUpdates;

  public TestSuiteSummaryDto(
      Long tests, Long failures, Long errors, Long skipped, Double time, Boolean hasUpdates) {
    this.tests = tests == null ? 0 : tests.intValue();
    this.failures = failures == null ? 0 : failures.intValue();
    this.errors = errors == null ? 0 : errors.intValue();
    this.skipped = skipped == null ? 0 : skipped.intValue();
    this.time = time == null ? 0 : time;
    this.hasUpdates = hasUpdates == null ? false : hasUpdates;
  }
}
