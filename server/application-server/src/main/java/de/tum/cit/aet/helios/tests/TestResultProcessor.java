package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.tests.parsers.JunitParser;
import de.tum.cit.aet.helios.tests.parsers.TestParserResult;
import de.tum.cit.aet.helios.workflow.Workflow;
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
  private final TestResultRepository testResultRepository;
  private final JunitParser junitParser;
  private final WorkflowService workflowService;

  @Value("${tests.artifactName:Test Results}")
  private String testArtifactName;

  public boolean shouldProcess(WorkflowRun workflowRun) {
    log.debug(
        "Checking if test results should be processed for workflow run {}", workflowRun.getName());

    if (workflowRun.getStatus() != WorkflowRun.Status.COMPLETED) {
      return false;
    }

    final Workflow testWorkflow =
        this.workflowService.getTestWorkflow(workflowRun.getRepository().getRepositoryId());

    if (testWorkflow == null || workflowRun.getWorkflowId() != testWorkflow.getId()) {
      return false;
    }

    return this.testResultRepository.findByWorkflowRun(workflowRun).isEmpty();
  }

  @Async("testResultProcessorExecutor")
  public void processRun(WorkflowRun workflowRun) {
    log.debug("Processing test results for workflow run {}", workflowRun.getName());

    GHArtifact testResultsArtifact = null;

    // thread sleep 2 seconds
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

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

    List<TestParserResult> results;

    try {
      results = this.processTestResultArtifact(testResultsArtifact);
    } catch (IOException e) {
      throw new TestResultException("Failed to process test results artifact", e);
    }

    log.debug("Parsed {} test results. Persisting...", results.size());

    results.forEach(
        result -> {
          final TestResult testResult = new TestResult();
          testResult.setWorkflowRun(workflowRun);
          testResult.setTotal(result.total());
          testResult.setPassed(result.passed());
          testResult.setFailures(result.failures());
          testResult.setErrors(result.errors());
          testResult.setSkipped(result.skipped());
          testResultRepository.save(testResult);
        });

    log.debug("Persisted test results");
  }

  private List<TestParserResult> processTestResultArtifact(GHArtifact artifact) throws IOException {
    // Download the ZIP artifact, find all parsable XML files and parse them
    return artifact.download(
        stream -> {
          if (stream.available() == 0) {
            throw new TestResultException("Empty artifact stream");
          }

          List<TestParserResult> results = new ArrayList<>();

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
