package de.tum.cit.aet.helios.tests.parsers;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

@Component
public class JunitParser implements TestResultParser {
  private static final int MAX_FAILURE_MESSAGE_CHARS = 8_192;
  private static final int MAX_FAILURE_TYPE_CHARS = 255;
  private static final int MAX_STACK_TRACE_CHARS = 65_536;
  private static final int MAX_SYSTEM_OUT_CHARS = 65_536;
  private static final String TRUNCATION_SUFFIX = "\n...[truncated]";

  private static LocalDateTime parseDateTime(String dateTime) {
    try {
      // Try parsing as OffsetDateTime first (for Z suffix)
      return OffsetDateTime.parse(dateTime).toLocalDateTime();
    } catch (DateTimeParseException e) {
      // Fallback to LocalDateTime parsing if no Z
      return LocalDateTime.parse(dateTime);
    }
  }

  /**
   * Parses JUnit XML test results from an input stream. Handles both single test suite and multiple
   * test suites XML formats.
   *
   * @param inputStream The input stream containing the JUnit XML content
   * @return A list of parsed test suites
   * @throws TestResultParseException if there is an error parsing the XML content
   */
  @Override
  public List<TestResultParser.TestSuite> parse(InputStream inputStream)
      throws TestResultParseException {
    XMLStreamReader reader = null;
    try {
      reader = createXmlStreamReader(inputStream);
      while (reader.hasNext()) {
        if (reader.next() != XMLStreamConstants.START_ELEMENT) {
          continue;
        }

        if ("testsuites".equals(reader.getLocalName())) {
          return parseTestSuites(reader);
        }
        if ("testsuite".equals(reader.getLocalName())) {
          return Collections.singletonList(parseTestSuite(reader));
        }

        throw new TestResultParseException(
            "Unexpected root element type: " + reader.getLocalName());
      }

      throw new TestResultParseException("Missing root element");
    } catch (XMLStreamException e) {
      throw new TestResultParseException("Failed to parse JUnit XML", e);
    } finally {
      closeReader(reader);
    }
  }

  @Override
  public boolean supports(String fileName) {
    return (fileName.startsWith("TEST-") && fileName.endsWith(".xml"))
        || fileName.equals("results.xml")
        || fileName.equals("junit.xml");
  }

  private XMLStreamReader createXmlStreamReader(InputStream inputStream)
      throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    return factory.createXMLStreamReader(inputStream);
  }

  private List<TestResultParser.TestSuite> parseTestSuites(XMLStreamReader reader)
      throws XMLStreamException {
    List<TestResultParser.TestSuite> suites = new ArrayList<>();
    while (reader.hasNext()) {
      int eventType = reader.next();
      if (eventType == XMLStreamConstants.START_ELEMENT
          && "testsuite".equals(reader.getLocalName())) {
        suites.add(parseTestSuite(reader));
      } else if (eventType == XMLStreamConstants.END_ELEMENT
          && "testsuites".equals(reader.getLocalName())) {
        return suites;
      }
    }
    return suites;
  }

  private TestResultParser.TestSuite parseTestSuite(XMLStreamReader reader)
      throws XMLStreamException {
    String name = reader.getAttributeValue(null, "name");
    int tests = parseIntAttribute(reader, "tests");
    int failures = parseIntAttribute(reader, "failures");
    int errors = parseIntAttribute(reader, "errors");
    int skipped = parseIntAttribute(reader, "skipped");
    double time = parseDoubleAttribute(reader, "time");
    String timestamp = reader.getAttributeValue(null, "timestamp");
    boolean keepSuiteSystemOut = failures > 0 || errors > 0;
    TextAccumulator suiteSystemOut =
        keepSuiteSystemOut ? new TextAccumulator(MAX_SYSTEM_OUT_CHARS) : null;
    List<TestResultParser.TestCase> testCases = new ArrayList<>();

    while (reader.hasNext()) {
      int eventType = reader.next();
      if (eventType == XMLStreamConstants.START_ELEMENT) {
        switch (reader.getLocalName()) {
          case "testcase" -> testCases.add(parseTestCase(reader));
          case "system-out" -> {
            if (suiteSystemOut != null) {
              suiteSystemOut.appendElementText(reader, "\n");
            } else {
              skipElement(reader);
            }
          }
          default -> skipElement(reader);
        }
      } else if (eventType == XMLStreamConstants.END_ELEMENT
          && "testsuite".equals(reader.getLocalName())) {
        return new TestResultParser.TestSuite(
            name,
            parseDateTime(timestamp),
            tests,
            failures,
            errors,
            skipped,
            time,
            suiteSystemOut == null ? null : suiteSystemOut.build(),
            testCases);
      }
    }

    throw new XMLStreamException("Unexpected end of document while parsing testsuite");
  }

  private TestResultParser.TestCase parseTestCase(XMLStreamReader reader)
      throws XMLStreamException {
    String name = reader.getAttributeValue(null, "name");
    String className = reader.getAttributeValue(null, "classname");
    double time = parseDoubleAttribute(reader, "time");
    boolean failed = false;
    boolean errored = false;
    boolean skipped = false;
    String errorType = null;
    String message = null;
    String stackTrace = null;
    TextAccumulator systemOut = new TextAccumulator(MAX_SYSTEM_OUT_CHARS);

    while (reader.hasNext()) {
      int eventType = reader.next();
      if (eventType == XMLStreamConstants.START_ELEMENT) {
        switch (reader.getLocalName()) {
          case "failure" -> {
            FailureDetails details = parseFailureDetails(reader);
            if (!failed) {
              errorType = details.type();
              message = details.message();
              stackTrace = details.content();
            }
            failed = true;
          }
          case "error" -> {
            FailureDetails details = parseFailureDetails(reader);
            if (!failed && !errored) {
              errorType = details.type();
              message = details.message();
              stackTrace = details.content();
            }
            errored = true;
          }
          case "skipped" -> {
            skipped = true;
            skipElement(reader);
          }
          case "system-out" -> systemOut.appendElementText(reader, "\n");
          default -> skipElement(reader);
        }
      } else if (eventType == XMLStreamConstants.END_ELEMENT
          && "testcase".equals(reader.getLocalName())) {
        return new TestResultParser.TestCase(
            name,
            className,
            time,
            failed,
            errored,
            skipped,
            errorType,
            message,
            stackTrace,
            failed || errored ? systemOut.build() : null);
      }
    }

    throw new XMLStreamException("Unexpected end of document while parsing testcase");
  }

  private FailureDetails parseFailureDetails(XMLStreamReader reader) throws XMLStreamException {
    String message =
        truncateAttribute(
            reader.getAttributeValue(null, "message"), MAX_FAILURE_MESSAGE_CHARS);
    String type =
        truncateAttribute(reader.getAttributeValue(null, "type"), MAX_FAILURE_TYPE_CHARS);
    TextAccumulator content = new TextAccumulator(MAX_STACK_TRACE_CHARS);
    content.appendElementText(reader, "");
    return new FailureDetails(type, message, content.build());
  }

  private int parseIntAttribute(XMLStreamReader reader, String attributeName) {
    String value = reader.getAttributeValue(null, attributeName);
    return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
  }

  private double parseDoubleAttribute(XMLStreamReader reader, String attributeName) {
    String value = reader.getAttributeValue(null, attributeName);
    return value == null || value.isBlank() ? 0.0 : Double.parseDouble(value);
  }

  private String truncateAttribute(String value, int maxChars) {
    if (value == null || value.length() <= maxChars) {
      return value;
    }
    return value.substring(0, maxChars);
  }

  private static void skipElement(XMLStreamReader reader) throws XMLStreamException {
    int depth = 1;
    while (reader.hasNext() && depth > 0) {
      int eventType = reader.next();
      if (eventType == XMLStreamConstants.START_ELEMENT) {
        depth++;
      } else if (eventType == XMLStreamConstants.END_ELEMENT) {
        depth--;
      }
    }
  }

  private void closeReader(XMLStreamReader reader) {
    if (reader == null) {
      return;
    }

    try {
      reader.close();
    } catch (XMLStreamException ignored) {
      // Ignore close failures for parser cleanup.
    }
  }

  private record FailureDetails(String type, String message, String content) {}

  private static final class TextAccumulator {
    private final int maxChars;
    private final int maxContentChars;
    private final StringBuilder builder = new StringBuilder();
    private boolean truncated;

    private TextAccumulator(int maxChars) {
      this.maxChars = maxChars;
      this.maxContentChars = Math.max(0, maxChars - TRUNCATION_SUFFIX.length());
    }

    private void appendElementText(XMLStreamReader reader, String separator)
        throws XMLStreamException {
      boolean appendSeparator = hasContent();
      boolean sawTextInElement = false;

      while (reader.hasNext()) {
        int eventType = reader.next();
        if (eventType == XMLStreamConstants.CHARACTERS
            || eventType == XMLStreamConstants.CDATA
            || eventType == XMLStreamConstants.SPACE) {
          String text = reader.getText();
          if (text == null || text.isEmpty()) {
            continue;
          }
          if (!sawTextInElement && appendSeparator) {
            append(separator);
          }
          append(text);
          sawTextInElement = true;
        } else if (eventType == XMLStreamConstants.START_ELEMENT) {
          skipElement(reader);
        } else if (eventType == XMLStreamConstants.END_ELEMENT) {
          return;
        }
      }
    }

    private void append(String text) {
      if (text == null || text.isEmpty()) {
        return;
      }

      int remaining = maxContentChars - builder.length();
      if (remaining > 0) {
        builder.append(text, 0, Math.min(remaining, text.length()));
      }
      if (text.length() > remaining) {
        truncated = true;
      }
    }

    private boolean hasContent() {
      return !builder.isEmpty() || truncated;
    }

    private String build() {
      if (!hasContent()) {
        return null;
      }
      String value = builder.toString();
      if (!truncated) {
        return value;
      }
      if (value.length() >= maxChars) {
        return value.substring(0, maxChars);
      }
      return value + TRUNCATION_SUFFIX;
    }
  }
}
