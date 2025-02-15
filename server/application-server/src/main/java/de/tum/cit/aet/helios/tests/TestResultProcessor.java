package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.tests.parsers.JunitParser;
import de.tum.cit.aet.helios.tests.parsers.TestResultParser;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowService;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class TestResultProcessor {
  private final GitHubService gitHubService;
  private final TestSuiteRepository testSuiteRepository;
  private final JunitParser junitParser;
  private final WorkflowService workflowService;

  @Value("${tests.artifactName:JUnit Test Results}")
  private String testArtifactName;

  public boolean shouldProcess(WorkflowRun workflowRun) {
    log.debug(
        "Checking if test results should be processed for workflow run {}", workflowRun.getName());

    if (workflowRun.getStatus() != WorkflowRun.Status.COMPLETED) {
      return false;
    }

    var testWorkflows =
        this.workflowService.getTestWorkflows(workflowRun.getRepository().getRepositoryId());

    if (testWorkflows.stream().noneMatch(w -> w.getId().equals(workflowRun.getWorkflowId()))) {
      return false;
    }

    // We don't want to process test results twice
    return this.testSuiteRepository.findByWorkflowRunId(workflowRun.getId()).isEmpty();
  }

  @Async("testResultProcessorExecutor")
  public void processRun(WorkflowRun workflowRun) {
    log.debug("Processing test results for workflow run {}", workflowRun.getName());

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

    this.testSuiteRepository.saveAll(testSuites);

    log.debug("Persisted test results");
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

                  results.add(this.junitParser.parse(nonClosingStream));
                }
              }
              zipInput.closeEntry();
            }
          }

          return results;
        });
  }
}
