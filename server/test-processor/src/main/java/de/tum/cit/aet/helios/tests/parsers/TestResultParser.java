package de.tum.cit.aet.helios.tests.parsers;

import de.tum.cit.aet.helios.common.dto.test.TestSuite;
import java.io.InputStream;
import java.util.List;

public interface TestResultParser {
  List<TestSuite> parse(InputStream content) throws TestResultParseException;

  boolean supports(String artifactName);
}
