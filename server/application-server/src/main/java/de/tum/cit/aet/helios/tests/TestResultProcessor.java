package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.tests.parsers.JunitParser;
import de.tum.cit.aet.helios.tests.parsers.TestResultParseException;
import de.tum.cit.aet.helios.tests.parsers.TestResultParser;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.io.FilterInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class TestResultProcessor {
  private final GitHubService gitHubService;
  private final WorkflowRunRepository workflowRunRepository;
  private final JunitParser junitParser;

  @Value("${tests.artifactName:JUnit Test Results}")
  private String testArtifactName;

  public boolean shouldProcess(WorkflowRun workflowRun) {
    log.debug(
        "Checking if test results should be processed for workflow run {}", workflowRun.getName());

    if (workflowRun.getStatus() != WorkflowRun.Status.COMPLETED
        || workflowRun.getWorkflow().getLabel() != Workflow.Label.TEST) {
      return false;
    }

    // If it's older than 2 hours from now, don't process it. That should usually not
    // happen. But in case we are receiving old events from NATS, we should not risk
    // processing a lot of old runs (e.g. when the server was down).
    if (workflowRun.getUpdatedAt().plusHours(2).isBefore(OffsetDateTime.now())) {
      return false;
    }

    return workflowRun.getTestProcessingStatus() == null;
  }

  @Async("testResultProcessorExecutor")
  public void processRun(WorkflowRun workflowRun) {
    log.debug("Processing test results for workflow run {}", workflowRun.getName());

    workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSING);
    this.workflowRunRepository.save(workflowRun);

    try {
      workflowRun.setTestSuites(this.processRunSync(workflowRun));
      workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSED);
      log.debug("Successfully persisted test results for workflow run {}", workflowRun.getName());
    } catch (Exception e) {
      log.error("Failed to process test results for workflow run {}", workflowRun.getName(), e);
      workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.FAILED);
      workflowRun.setTestSuites(null);
    } finally {
      this.workflowRunRepository.save(workflowRun);
    }
  }

  private List<TestSuite> processRunSync(WorkflowRun workflowRun) {
    GHArtifact testResultsArtifact = null;

    try {
      PagedIterable<GHArtifact> artifacts =
          this.gitHubService.getWorkflowRunArtifacts(
              workflowRun.getRepository().getRepositoryId(), workflowRun.getId());

      // Traverse page iterable to find the first artifact with the configured name
      for (GHArtifact artifact : artifacts) {
        if (artifact.getName().equals(this.testArtifactName)) {
          testResultsArtifact = artifact;
          break;
        }
      }
    } catch (IOException e) {
      throw new TestResultException("Failed to fetch artifacts", e);
    }

    if (testResultsArtifact == null) {
      throw new TestResultException("Test results artifact not found");
    }

    log.debug("Found test results artifact {}", testResultsArtifact.getName());

    List<TestResultParser.TestSuite> results;

    try {
      results = this.processTestResultArtifact(testResultsArtifact);
    } catch (IOException e) {
      throw new TestResultException("Failed to process test results artifact", e);
    }

    log.debug("Parsed {} test suits. Persisting...", results.size());

    List<TestSuite> testSuites = new ArrayList<>();

    for (TestResultParser.TestSuite result : results) {
      TestSuite testSuite = new TestSuite();
      testSuite.setWorkflowRun(workflowRun);
      testSuite.setName(result.name());
      testSuite.setTests(result.tests());
      testSuite.setFailures(result.failures());
      testSuite.setErrors(result.errors());
      testSuite.setSkipped(result.skipped());
      testSuite.setTime(result.time());
      testSuite.setTimestamp(result.timestamp());
      testSuite.setTestCases(
          result.testCases().stream()
              .map(
                  tc -> {
                    TestCase testCase = new TestCase();
                    testCase.setTestSuite(testSuite);
                    testCase.setName(tc.name());
                    testCase.setClassName(tc.className());
                    testCase.setTime(tc.time());
                    testCase.setStatus(
                        tc.failed()
                            ? TestCase.TestStatus.FAILED
                            : tc.error()
                                ? TestCase.TestStatus.ERROR
                                : tc.skipped()
                                    ? TestCase.TestStatus.SKIPPED
                                    : TestCase.TestStatus.PASSED);
                    testCase.setMessage(tc.message());
                    testCase.setStackTrace(tc.stackTrace());
                    testCase.setErrorType(tc.errorType());
                    return testCase;
                  })
              .toList());
      testSuites.add(testSuite);
    }

    return testSuites;
  }

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
                    results.add(this.junitParser.parse(nonClosingStream));
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
}
