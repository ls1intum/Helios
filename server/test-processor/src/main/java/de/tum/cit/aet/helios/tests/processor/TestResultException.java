package de.tum.cit.aet.helios.tests.processor;

public class TestResultException extends RuntimeException {
  public TestResultException(String message) {
    super(message);
  }

  public TestResultException(String message, Throwable cause) {
    super(message, cause);
  }
}
