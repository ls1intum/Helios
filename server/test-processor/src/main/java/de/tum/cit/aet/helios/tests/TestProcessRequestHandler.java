package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.common.github.GitHubFacade;
import de.tum.cit.aet.helios.common.nats.JacksonMessageHandler;
import de.tum.cit.aet.helios.tests.parsers.JunitParser;
import de.tum.cit.aet.helios.tests.parsers.TestResultParseException;
import de.tum.cit.aet.helios.tests.parsers.TestResultParser;
import de.tum.cit.aet.helios.tests.parsers.TestResultParser.TestSuite;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.PagedIterable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class TestProcessRequestHandler extends JacksonMessageHandler<TestProcessRequestPayload> {
  private final GitHubFacade github;
  private final JunitParser junitParser;

  @Value("${tests.artifactName:JUnit Test Results}")
  private String testArtifactName;

  @Async("testResultProcessorExecutor")
  public void processRequest(TestProcessRequestPayload request) {
    this.processRequestSync(request);
  }

  /**
   * Processes a workflow run's test results synchronously.
   *
   * @param workflowRun the workflow run to process
   * @return the test suites extracted from the workflow run's artifacts
   */
  private List<TestSuite> processRequestSync(TestProcessRequestPayload request) {
    GHArtifact testResultsArtifact = null;

    try {
      PagedIterable<GHArtifact> artifacts =
          this.github
              .getRepositoryById(request.repositoryId())
              .getWorkflowRun(request.workflowRunId())
              .listArtifacts();

      // Traverse page iterable to find the first artifact with the configured name
      for (GHArtifact artifact : artifacts) {
        if (artifact.getName().equals(testArtifactName)) {
          testResultsArtifact = artifact;
          break;
        }
      }
    } catch (IOException e) {
      throw new TestResultException("Failed to fetch artifacts", e);
    }

    if (testResultsArtifact == null) {
      throw new TestResultException("Test results artifact not found: " + testArtifactName);
    }

    log.debug("Found test results artifact {}", testResultsArtifact.getName());

    List<TestResultParser.TestSuite> results;

    try {
      results = this.processTestResultArtifact(testResultsArtifact);
    } catch (IOException e) {
      throw new TestResultException("Failed to process test results artifact", e);
    }

    return results;
  }

  /**
   * Processes a test result artifact.
   *
   * @param artifact the artifact to process
   * @return the test suites extracted from the artifact
   * @throws IOException if an I/O error occurs
   */
  private List<TestResultParser.TestSuite> processTestResultArtifact(GHArtifact artifact)
      throws IOException {
    // Download the ZIP artifact, find all parsable XML files and parse them
    return artifact.download(
        stream -> {
          if (stream.available() == 0) {
            throw new TestResultException("Empty artifact stream");
          }

          List<TestResultParser.TestSuite> results = new ArrayList<>();

          try (ZipInputStream zipInput = new ZipInputStream(stream)) {
            ZipEntry entry;

            while ((entry = zipInput.getNextEntry()) != null) {
              if (!entry.isDirectory()) {
                if (this.junitParser.supports(entry.getName())) {
                  var nonClosingStream =
                      new FilterInputStream(zipInput) {
                        @Override
                        public void close() throws IOException {
                          // Do nothing, so the underlying stream stays open.
                        }
                      };

                  try {
                    results.addAll(this.junitParser.parse(nonClosingStream));
                  } catch (TestResultParseException e) {
                    log.error("Failed to parse JUnit XML file {}", entry.getName(), e);
                  }
                }
              }

              zipInput.closeEntry();
            }
          }

          return results;
        });
  }

  @Override
  protected Class<TestProcessRequestPayload> getPayloadClass() {
    return TestProcessRequestPayload.class;
  }

  @Override
  protected void handleMessage(TestProcessRequestPayload payload) {
    this.processRequest(payload);
  }

  @Override
  public String getSubjectPattern() {
    return "helios.tests.process";
  }
}
