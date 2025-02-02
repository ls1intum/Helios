package de.tum.cit.aet.helios.tests.parsers;

import java.io.InputStream;

public interface TestResultParser {
    TestParserResult parse(InputStream content);

    boolean supports(String artifactName);
}
