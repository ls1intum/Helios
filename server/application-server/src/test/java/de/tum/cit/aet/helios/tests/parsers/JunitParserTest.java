package de.tum.cit.aet.helios.tests.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
