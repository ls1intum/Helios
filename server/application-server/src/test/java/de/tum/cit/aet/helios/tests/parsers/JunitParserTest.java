package de.tum.cit.aet.helios.tests.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class JunitParserTest {

  private final JunitParser junitParser = new JunitParser();

  @Test
  void parse_shouldSkipSystemOutForPassingSuiteAndCase() throws TestResultParseException {
    String xml =
        """
        <testsuite name="suite" tests="1" failures="0" errors="0"
            skipped="0" time="1.0" timestamp="2026-03-27T11:34:31Z">
          <testcase name="passed" classname="pkg.Test" time="0.1">
            <system-out>passed output</system-out>
          </testcase>
          <system-out>suite output</system-out>
        </testsuite>
        """;

    List<TestResultParser.TestSuite> suites =
        junitParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(1, suites.size());
    assertNull(suites.getFirst().systemOut());
    assertNull(suites.getFirst().testCases().getFirst().systemOut());
  }

  @Test
  void parse_shouldKeepSystemOutForFailingSuiteAndCase() throws TestResultParseException {
    String xml =
        """
        <testsuite name="suite" tests="1" failures="1" errors="0"
            skipped="0" time="1.0" timestamp="2026-03-27T11:34:31Z">
          <testcase name="failed" classname="pkg.Test" time="0.1">
            <failure message="boom" type="AssertionError">stack trace</failure>
            <system-out>failed output</system-out>
          </testcase>
          <system-out>suite output</system-out>
        </testsuite>
        """;

    List<TestResultParser.TestSuite> suites =
        junitParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(1, suites.size());
    assertEquals("suite output", suites.getFirst().systemOut());
    assertEquals("failed output", suites.getFirst().testCases().getFirst().systemOut());
  }

  @Test
  void parse_shouldHandleTestsuitesRoot() throws TestResultParseException {
    String xml =
        """
        <testsuites>
          <testsuite name="suite-a" tests="1" failures="0" errors="0"
              skipped="0" time="1.0" timestamp="2026-03-27T11:34:31Z">
            <testcase name="passed-a" classname="pkg.TestA" time="0.1" />
          </testsuite>
          <testsuite name="suite-b" tests="1" failures="0" errors="0"
              skipped="0" time="2.0" timestamp="2026-03-27T11:35:31Z">
            <testcase name="passed-b" classname="pkg.TestB" time="0.2" />
          </testsuite>
        </testsuites>
        """;

    List<TestResultParser.TestSuite> suites =
        junitParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertEquals(2, suites.size());
    assertEquals("suite-a", suites.get(0).name());
    assertEquals("suite-b", suites.get(1).name());
  }

  @Test
  void parse_shouldTruncateOversizedFailurePayloads() throws TestResultParseException {
    String longMessage = "m".repeat(10_000);
    String longStackTrace = "s".repeat(70_000);
    String longSystemOut = "o".repeat(70_000);
    String xml =
        """
        <testsuite name="suite" tests="1" failures="1" errors="0"
            skipped="0" time="1.0" timestamp="2026-03-27T11:34:31Z">
          <testcase name="failed" classname="pkg.Test" time="0.1">
            <failure message="%s" type="AssertionError">%s</failure>
            <system-out>%s</system-out>
          </testcase>
          <system-out>%s</system-out>
        </testsuite>
        """
            .formatted(longMessage, longStackTrace, longSystemOut, longSystemOut);

    List<TestResultParser.TestSuite> suites =
        junitParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    TestResultParser.TestCase testCase = suites.getFirst().testCases().getFirst();
    assertEquals(8_192, testCase.message().length());
    assertEquals(65_536, testCase.stackTrace().length());
    assertTrue(testCase.stackTrace().endsWith("[truncated]"));
    assertEquals(65_536, testCase.systemOut().length());
    assertTrue(testCase.systemOut().endsWith("[truncated]"));
    assertEquals(65_536, suites.getFirst().systemOut().length());
    assertTrue(suites.getFirst().systemOut().endsWith("[truncated]"));
  }
}
