package de.tum.cit.aet.helios.tests.parsers;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

public interface TestResultParser {
  List<TestSuite> parse(InputStream content) throws TestResultParseException;

  boolean supports(String artifactName);

  public static record TestSuite(
      String name,
      LocalDateTime timestamp,
      Integer tests,
      Integer failures,
      Integer errors,
      Integer skipped,
      Double time,
      String systemOut,
      List<TestCase> testCases) {}

  public static record TestCase(
      String name,
      String className,
      Double time,
      boolean failed,
      boolean error,
      boolean skipped,
      String errorType,
      String message,
      String stackTrace,
      String systemOut) {}
}
