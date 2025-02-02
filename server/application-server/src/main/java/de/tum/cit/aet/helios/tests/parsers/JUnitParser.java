package de.tum.cit.aet.helios.tests.parsers;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.InputStream;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class JUnitParser implements TestResultParser {
  public TestParserResult parse(InputStream inputStream) {
    try {
      JAXBContext context = JAXBContext.newInstance(TestSuite.class);
      Unmarshaller unmarshaller = context.createUnmarshaller();
      TestSuite suite = (TestSuite) unmarshaller.unmarshal(inputStream);

      return new TestParserResult(
          suite.tests,
          suite.failures,
          suite.errors,
          suite.skipped,
          suite.time);
    } catch (JAXBException e) {
      throw new TestResultParseException("Failed to parse JUnit XML", e);
    }
  }

  public boolean supports(String fileName) {
    return fileName.startsWith("TEST-") && fileName.endsWith(".xml");
  }

  @XmlRootElement(name = "testsuite")
  public static class TestSuite {
    @XmlAttribute
    public int tests;
    @XmlAttribute
    public int failures;
    @XmlAttribute
    public int errors;
    @XmlAttribute
    public int skipped;
    @XmlAttribute
    public double time;
  }
}
