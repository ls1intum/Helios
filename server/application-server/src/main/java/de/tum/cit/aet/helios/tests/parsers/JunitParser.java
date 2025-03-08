package de.tum.cit.aet.helios.tests.parsers;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class JunitParser implements TestResultParser {
  private static LocalDateTime parseDateTime(String dateTime) {
    try {
      // Try parsing as OffsetDateTime first (for Z suffix)
      return OffsetDateTime.parse(dateTime).toLocalDateTime();
    } catch (DateTimeParseException e) {
      // Fallback to LocalDateTime parsing if no Z
      return LocalDateTime.parse(dateTime);
    }
  }

  public TestResultParser.TestSuite parse(InputStream inputStream) throws TestResultParseException {
    try {
      JAXBContext context = JAXBContext.newInstance(TestSuite.class);
      Unmarshaller unmarshaller = context.createUnmarshaller();
      TestSuite suite = (TestSuite) unmarshaller.unmarshal(inputStream);

      return new TestResultParser.TestSuite(
          suite.name,
          parseDateTime(suite.timestamp),
          suite.tests,
          suite.failures,
          suite.errors,
          suite.skipped,
          suite.time,
          suite.testcases.stream().map(this::parseTestCase).toList());
    } catch (JAXBException e) {
      throw new TestResultParseException("Failed to parse JUnit XML", e);
    }
  }

  public boolean supports(String fileName) {
    return fileName.startsWith("TEST-") && fileName.endsWith(".xml");
  }

  private TestResultParser.TestCase parseTestCase(TestCase tc) {
    String errorType = null;
    String message = null;
    String stackTrace = null;

    if (tc.failure != null) {
      errorType = tc.failure.type;
      message = tc.failure.message;
      stackTrace = tc.failure.content;
    } else if (tc.error != null) {
      errorType = tc.error.type;
      message = tc.error.message;
      stackTrace = tc.error.content;
    }

    return new TestResultParser.TestCase(
        tc.name,
        tc.className,
        tc.time,
        tc.failure != null,
        tc.error != null,
        tc.skipped != null,
        errorType,
        message,
        stackTrace);
  }

  @XmlRootElement(name = "testsuite")
  public static class TestSuite {
    @XmlAttribute public String name;
    @XmlAttribute public int tests;
    @XmlAttribute public int failures;
    @XmlAttribute public int errors;
    @XmlAttribute public int skipped;
    @XmlAttribute public double time;
    @XmlAttribute public String timestamp;

    @XmlElement(name = "testcase")
    public List<TestCase> testcases = new ArrayList<>();
  }

  public static class TestCase {
    @XmlAttribute public String name;

    @XmlAttribute(name = "classname")
    public String className;

    @XmlAttribute public double time;

    @XmlElement(name = "failure")
    public TestCaseFailure failure;

    @XmlElement(name = "error")
    public TestCaseError error;

    @XmlElement(name = "skipped")
    public TestCaseSkipped skipped;
  }

  public static class TestCaseFailure {
    @XmlAttribute public String message;

    @XmlAttribute public String type;

    @XmlValue public String content;
  }

  public static class TestCaseError {
    @XmlAttribute public String message;

    @XmlAttribute public String type;

    @XmlValue public String content;
  }

  public static class TestCaseSkipped {}
}
