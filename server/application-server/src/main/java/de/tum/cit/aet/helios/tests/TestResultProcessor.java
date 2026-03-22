package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.parsers.JunitParser;
import de.tum.cit.aet.helios.tests.parsers.TestResultParseException;
import de.tum.cit.aet.helios.tests.parsers.TestResultParser;
import de.tum.cit.aet.helios.tests.type.TestType;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.io.FilterInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.PagedIterable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class TestResultProcessor {
  private final GitHubService gitHubService;
  private final WorkflowRunRepository workflowRunRepository;
  private final GitRepoRepository gitRepoRepository;
  private final JunitParser junitParser;
  private final TestCaseStatisticsService statisticsService;
  private final TestCaseRepository testCaseRepository;

  /**
   * Determines if a workflow run's test results should be processed.
   *
   * @param workflowRun the workflow run to check
   * @return true if the workflow run's test results should be processed, false otherwise
   */
  public boolean shouldProcess(WorkflowRun workflowRun) {
    log.debug(
        "Checking if test results should be processed for workflow run, workflow name {}",
        workflowRun.getName());

    if (workflowRun.getStatus() != WorkflowRun.Status.COMPLETED
        || workflowRun.getWorkflow().getTestTypes().isEmpty()) {
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

  /**
   * Processes a workflow run's test results asynchronously.
   *
   * @param workflowRun the workflow run to process
   */
  @Async("testResultProcessorExecutor")
  public void processRun(WorkflowRun workflowRun) {
    log.debug("Processing test results for workflow run {}", workflowRun.getName());

    workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSING);
    this.workflowRunRepository.save(workflowRun);

    try {
      List<TestSuiteRun> testSuiteRuns = this.processRunSync(workflowRun);
      workflowRun.setTestSuiteRuns(testSuiteRuns);
      workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSED);
      log.debug(
          "Successfully persisted test results for workflow run, workflow name: {}",
          workflowRun.getName());

      // Update test statistics if the workflow run is on the default branch
      updateTestStatisticsIfDefaultBranch(testSuiteRuns, workflowRun);
    } catch (Exception e) {
      log.error("Failed to process test results for workflow run {}", workflowRun.getName(), e);
      workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.FAILED);
      workflowRun.setTestSuiteRuns(null);
    } finally {
      this.workflowRunRepository.save(workflowRun);
    }
  }

  /**
   * Processes a workflow run's test results synchronously.
   *
   * @param workflowRun the workflow run to process
   * @return the test suites extracted from the workflow run's artifacts
   */
  private List<TestSuiteRun> processRunSync(WorkflowRun workflowRun) {
    List<TestSuiteRun> allTestSuiteRuns = new ArrayList<>();

    try {
      PagedIterable<GHArtifact> artifacts =
          this.gitHubService.getWorkflowRunArtifacts(
              workflowRun.getRepository().getRepositoryId(), workflowRun.getId());

      // Get all test types for this workflow
      Set<TestType> testTypes = workflowRun.getWorkflow().getTestTypes();

      // Process each artifact that matches a test type's artifact name
      for (GHArtifact artifact : artifacts) {
        TestType matchingTestType = findMatchingTestType(testTypes, artifact.getName());
        if (matchingTestType != null) {
          List<TestSuiteRun> testSuiteRuns =
              processTestResultArtifact(artifact, workflowRun.getRepository());
          // Set the test type relationship for each test suite
          testSuiteRuns.forEach(
              testSuiteRun -> {
                testSuiteRun.setWorkflowRun(workflowRun);
                testSuiteRun.setTestType(matchingTestType);
              });
          allTestSuiteRuns.addAll(testSuiteRuns);
          log.debug(
              "Processed artifact {} for test type {}",
              artifact.getName(),
              matchingTestType.getName());
        }
      }
    } catch (IOException e) {
      throw new TestResultException("Failed to fetch or process artifacts", e);
    }

    if (allTestSuiteRuns.isEmpty()) {
      log.warn("No matching test artifacts found for workflow run {}", workflowRun.getName());
      throw new TestResultException("No matching test artifacts found");
    }

    log.debug("Parsed {} test suites across all artifacts. Persisting...", allTestSuiteRuns.size());
    return allTestSuiteRuns;
  }

  private TestType findMatchingTestType(Set<TestType> testTypes, String artifactName) {
    return testTypes.stream()
        .filter(testType -> testType.getArtifactName().equals(artifactName))
        .findFirst()
        .orElse(null);
  }

  private List<TestSuiteRun> convertToTestSuites(
      List<TestResultParser.TestSuite> results, GitRepository repository) {
    return results.stream()
        .map(
            result -> {
              TestSuiteRun testSuiteRun = new TestSuiteRun();
              testSuiteRun.setName(result.name());
              testSuiteRun.setTests(result.tests());
              testSuiteRun.setFailures(result.failures());
              testSuiteRun.setErrors(result.errors());
              testSuiteRun.setSkipped(result.skipped());
              testSuiteRun.setTime(result.time());
              testSuiteRun.setTimestamp(result.timestamp());
              // We don't want to store system out for passed test suites, as it can be quite large
              if (result.failures() > 0 || result.errors() > 0) {
                testSuiteRun.setSystemOut(result.systemOut());
              } else {
                testSuiteRun.setSystemOut(null);
              }
              testSuiteRun.setTestCaseRuns(
                  result.testCases().stream()
                      .map(tc ->
                          createTestCaseRun(tc, testSuiteRun, repository))
                      .toList());
              return testSuiteRun;
            })
        .toList();
  }

  private TestCaseRun createTestCaseRun(
      TestResultParser.TestCase tc,
      TestSuiteRun testSuiteRun,
      GitRepository repository) {
    TestCase testCaseDef =
        testCaseRepository
            .findByRepositoryRepositoryIdAndSuiteNameAndClassNameAndName(
                repository.getRepositoryId(), testSuiteRun.getName(), tc.className(), tc.name())
            .orElseGet(
                () -> {
                  TestCase newDef = new TestCase();
                  newDef.setRepository(repository);
                  newDef.setSuiteName(testSuiteRun.getName());
                  newDef.setClassName(tc.className());
                  newDef.setName(tc.name());
                  return testCaseRepository.save(newDef);
                });

    TestCaseRun testCaseRun = new TestCaseRun();
    testCaseRun.setTestSuiteRun(testSuiteRun);
    testCaseRun.setTestCase(testCaseDef);
    testCaseRun.setTime(tc.time());
    testCaseRun.setStatus(
        tc.failed()
            ? TestCaseRun.TestStatus.FAILED
            : tc.error()
                ? TestCaseRun.TestStatus.ERROR
                : tc.skipped() ? TestCaseRun.TestStatus.SKIPPED : TestCaseRun.TestStatus.PASSED);
    testCaseRun.setMessage(tc.message());
    testCaseRun.setStackTrace(tc.stackTrace());
    testCaseRun.setErrorType(tc.errorType());

    // We don't want to store system out for passed tests, as it can be quite large
    if (tc.failed() || tc.error()) {
      testCaseRun.setSystemOut(tc.systemOut());
    } else {
      testCaseRun.setSystemOut(null);
    }
    return testCaseRun;
  }

  /**
   * Processes a test result artifact.
   *
   * @param artifact the artifact to process
   * @param repository the repository the artifact belongs to
   * @return the test suites extracted from the artifact
   * @throws IOException if an I/O error occurs
   */
  private List<TestSuiteRun> processTestResultArtifact(
      GHArtifact artifact, GitRepository repository) throws IOException {
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

          return convertToTestSuites(results, repository);
        });
  }

  /**
   * Updates test statistics if the workflow run is on the default branch. This method safely
   * retrieves the repository and default branch information.
   *
   * @param testSuiteRuns the test suite runs containing test cases
   * @param workflowRun the workflow run
   */
  @Transactional
  protected void updateTestStatisticsIfDefaultBranch(
      List<TestSuiteRun> testSuiteRuns, WorkflowRun workflowRun) {
    try {
      String headBranch = workflowRun.getHeadBranch();
      if (headBranch == null) {
        log.debug("Skipping test statistics update: head branch is null");
        return;
      }

      // Safely retrieve repository with its properties in a transaction
      Optional<GitRepository> repository =
          gitRepoRepository.findById(workflowRun.getRepository().getRepositoryId());

      if (repository.isEmpty()) {
        log.debug("Skipping test statistics update: repository not found");
        return;
      }

      String defaultBranch = repository.get().getDefaultBranch();

      if (headBranch.equals(defaultBranch)) {
        log.debug("Updating test statistics for default branch: {}", headBranch);
        // update statistics for the default branch
        updateTestStatistics(testSuiteRuns, headBranch);
      } else {
        log.debug(
            "Skipping test statistics update for non-default branch: {}, default branch: {}",
            headBranch,
            defaultBranch);
      }

      // update statistics for all the branches combined
      updateTestStatistics(testSuiteRuns, "combined");
      log.debug("Successfully updated test statistics for all branches combined");

      updateFlakinessScores(testSuiteRuns, defaultBranch, repository.get());
      log.debug("Successfully recomputed flakiness scores for affected tests");
    } catch (Exception e) {
      log.error("Error while trying to update test statistics", e);
      // Don't fail the overall process if statistics update fails
    }
  }

  /**
   * Updates test case statistics for all test cases in the given test suites.
   *
   * @param testSuiteRuns the test suite runs containing test case runs
   * @param branchName the branch name where the tests were run
   */
  private void updateTestStatistics(
      List<TestSuiteRun> testSuiteRuns, String branchName) {
    try {
      for (TestSuiteRun testSuiteRun : testSuiteRuns) {
        statisticsService.updateStatisticsForTestSuiteRun(testSuiteRun, branchName);
      }
      log.debug("Successfully updated test statistics for branch: {}", branchName);
    } catch (Exception e) {
      log.error("Failed to update test statistics for branch: {}", branchName, e);
      // Don't fail the overall process if statistics update fails
    }
  }

  /**
   * Recomputes and persists flakiness scores for all tests belonging to the given suites.
   * Called after both the default-branch and combined statistics have been updated for a run.
   * Only fetches stats rows for the suite names present in the current run, keeping
   * the query bounded.
   *
   * @param testSuiteRuns the suites processed in this run
   * @param defaultBranchName the repository's default branch name
   * @param repository the repository
   */
  public void updateFlakinessScores(
      List<TestSuiteRun> testSuiteRuns, String defaultBranchName, GitRepository repository) {
    try {
      statisticsService.updateFlakinessForTestSuiteRun(testSuiteRuns, defaultBranchName, repository);
      log.debug("Successfully updated flakiness info for repository: {}",
          repository.getRepositoryId());
    } catch (Exception e) {
      log.error("Failed to update flakiness info for repository: {}",
          repository.getRepositoryId(), e);
      // Don't fail the overall process if flakiness update fails
    }
  }
}
