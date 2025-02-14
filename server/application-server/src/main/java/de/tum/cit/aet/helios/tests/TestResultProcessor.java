package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.tests.parsers.JunitParser;
import de.tum.cit.aet.helios.tests.parsers.TestParserResult;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class TestResultProcessor {
  private final GitHubService gitHubService;
  private final WorkflowRunRepository workflowRunRepository;
  private final TestResultRepository testResultRepository;
  private final JunitParser junitParser;

  @Value("${tests.artifactName:Test Results}")
  private String testArtifactName;

  public void processRun(long workflowRunId) {
    final WorkflowRun workflowRun =
        this.workflowRunRepository
            .findById(workflowRunId)
            .orElseThrow(() -> new TestResultException("Workflow run not found"));

    log.debug("Processing test results for workflow run {}", workflowRunId);

    GHArtifact testResultsArtifact = null;

    try {
      PagedIterable<GHArtifact> artifacts =
          this.gitHubService.getWorkflowRunArtifacts(
              workflowRun.getRepository().getRepositoryId(), workflowRunId);

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
          List<TestParserResult> results = new ArrayList<>();

          try (ZipInputStream zipInput = new ZipInputStream(stream)) {
            ZipEntry entry;

            while ((entry = zipInput.getNextEntry()) != null) {
              if (!entry.isDirectory()) {
                if (this.junitParser.supports(entry.getName())) {
                  results.add(this.junitParser.parse(zipInput));
                }
              }
              zipInput.closeEntry();
            }
          }

          return results;
        });
  }
}
