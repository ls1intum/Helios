package de.tum.cit.aet.helios.ai.testfailure;

import lombok.Getter;

@Getter
public class TestFailureAnalysisRateLimitExceededException extends RuntimeException {
  private final long retryAfterSeconds;

  public TestFailureAnalysisRateLimitExceededException(String message, long retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
