package de.tum.cit.aet.helios.tests.processor.parsers;

public class TestResultParseException extends RuntimeException {
  public TestResultParseException(String message, Throwable cause) {
    super(message, cause);
  }

  public TestResultParseException(String message) {
    super(message);
  }
}
