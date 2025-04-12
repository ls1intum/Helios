package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestResultProcessor;
import de.tum.cit.aet.helios.workflow.GitHubWorkflowContext;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRunService;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.PagedIterable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class GitHubWorkflowRunMessageHandler
    extends GitHubMessageHandler<GHEventPayload.WorkflowRun> {
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubWorkflowRunSyncService workflowSyncService;
  private final TestResultProcessor testResultProcessor;
  private final GitHubService gitHubService;
  private final WorkflowRunService workflowRunService;
  private final WorkflowRunRepository workflowRunRepository;
  private final PullRequestRepository pullRequestRepository;
  @Qualifier("workflowRunTaskScheduler")
  private final TaskScheduler taskScheduler;

  @Override
  protected Class<GHEventPayload.WorkflowRun> getPayloadClass() {
    return GHEventPayload.WorkflowRun.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.WORKFLOW_RUN;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.WorkflowRun eventPayload) {
    var action = eventPayload.getAction();
    var repository = eventPayload.getRepository();
    var githubRun = eventPayload.getWorkflowRun();
    var githubEvent = githubRun.getEvent();

    log.info(
        "Received workflow run event for repository: {}, workflow run: {}, action: {}, event: {}",
        repository.getFullName(),
        githubRun.getUrl(),
        action,
        githubEvent);

    try {
      eventPayload
          .getWorkflowRun()
          .getPullRequests()
          .forEach(
              pr -> {
                log.info("PR: {}", pr.getUrl());
              });
    } catch (Exception e) {
      log.error("Error: {}", e.getMessage());
    }

    repositorySyncService.processRepository(eventPayload.getRepository());


    // Check if this is a workflow_run event
    // (??) When we check artifacts for each status,
    // then for the completed status artifact list return an empty list
    // When it is fixed, we may want to check the artifacts for each status
    // but, we need to delay the processing events when the event name is workflow_run
    // since in the beginning of the workflow, we are uploading the context file,
    // it may or may not be seen when the workflow_run event is received.
    if ("workflow_run".equalsIgnoreCase(githubEvent.name())) {
      log.info("Received workflow_run event, delaying processing");
      taskScheduler.schedule(
          () -> handleWorkflowRunEvent(eventPayload),
          Instant.now().plusSeconds(10)
      );
      return;
    }

    var run = workflowSyncService.processRun(githubRun);
    processTestResult(run);
  }

  private void handleWorkflowRunEvent(GHEventPayload.WorkflowRun eventPayload) {
    log.info("Processing workflow_run event with workflow run id: {}",
        eventPayload.getWorkflowRun().getId());
    var repository = eventPayload.getRepository();
    var githubRun = eventPayload.getWorkflowRun();

    GitHubWorkflowContext context = null;

    try {
      context =
          extractWorkflowContext(repository.getId(), githubRun.getId());
    } catch (Exception e) {
      log.error("Error while extracting workflow context: {}", e.getMessage());
      return;
    }

    if (context == null) {
      log.warn("No workflow context found for workflow run: {}", githubRun.getId());
      return;
    }

    log.info("Context found with triggering workflow run id: {}, head branch: {}, head sha: {}",
        context.runId(), context.headBranch(), context.headSha());

    var run = workflowSyncService.processRunWithContext(githubRun, context);
    processTestResult(run);
  }


  /**
   * Processes the test result for the given workflow run.
   *
   * @param run The workflow run to process
   */
  private void processTestResult(WorkflowRun run) {
    if (run != null && testResultProcessor.shouldProcess(run)) {
      testResultProcessor.processRun(run);
    }
  }

  /**
   * Extracts workflow context from the workflow-context artifact.
   *
   * @param repositoryId The ID of the repository
   * @param runId The ID of the workflow run
   * @return The extracted GitHubWorkflowContext or null if not found or error occurred
   */
  private GitHubWorkflowContext extractWorkflowContext(long repositoryId, long runId) {
    GHArtifact ghArtifact = null;

    // Fetch artifacts to get the triggering workflow run ID
    try {
      PagedIterable<GHArtifact> artifacts =
          this.gitHubService.getWorkflowRunArtifacts(repositoryId, runId);

      // First artifact with the configured name
      for (GHArtifact artifact : artifacts) {
        if (artifact.getName().equals("workflow-context")) {
          ghArtifact = artifact;
          break;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to fetch artifacts for workflow run: {}", e.getMessage());
      return null;
    }

    if (ghArtifact == null) {
      log.warn("No workflow-context artifact found for E2E Tests workflow_run: {}", runId);
      return null;
    }

    log.debug("Found artifact {}", ghArtifact.getName());

    try {
      // Parse the artifact to extract the triggering workflow information
      return parseWorkflowContextArtifact(ghArtifact);
    } catch (Exception e) {
      log.error("Failed to parse workflow context artifact: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Parses the workflow context artifact to extract the triggering workflow information.
   */
  private GitHubWorkflowContext parseWorkflowContextArtifact(GHArtifact artifact)
      throws IOException {
    // Download & Parse the artifact
    return artifact.download(
        artifactContent -> {
          if (artifactContent.available() == 0) {
            throw new RuntimeException("Empty artifact stream!");
          }

          Long workflowRunId = null;
          String headBranch = null;
          String headSha = null;

          try (ZipInputStream zipInput = new ZipInputStream(artifactContent)) {
            ZipEntry entry;

            while ((entry = zipInput.getNextEntry()) != null) {
              if (!entry.isDirectory()
                  && "workflow-context.txt".equalsIgnoreCase(entry.getName())) {

                var nonClosingStream =
                    new FilterInputStream(zipInput) {
                      @Override
                      public void close() throws IOException {
                        // Do nothing, so the underlying stream stays open.
                      }
                    };

                // Read file content
                try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(nonClosingStream))) {
                  String line;
                  while ((line = reader.readLine()) != null) {
                    // Skip empty lines
                    if (line.trim().isEmpty()) {
                      continue;
                    }

                    // Split each line by equals sign
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                      String key = parts[0].trim();
                      String value = parts[1].trim();

                      // Extract values
                      switch (key) {
                        case "TRIGGERING_WORKFLOW_RUN_ID" -> workflowRunId = Long.parseLong(value);
                        case "TRIGGERING_WORKFLOW_HEAD_BRANCH" -> headBranch = value;
                        case "TRIGGERING_WORKFLOW_HEAD_SHA" -> headSha = value;
                        default -> log.warn("Unknown key in workflow-context.txt: {}", key);
                      }
                    }
                  }
                }
              }
              zipInput.closeEntry();
            }
          }

          // Validate that we found all required values
          if (workflowRunId == null) {
            throw new RuntimeException("Could not find TRIGGERING_WORKFLOW_RUN_ID in artifact");
          }
          if (headBranch == null) {
            throw new RuntimeException(
                "Could not find TRIGGERING_WORKFLOW_HEAD_BRANCH in artifact");
          }
          if (headSha == null) {
            throw new RuntimeException("Could not find TRIGGERING_WORKFLOW_HEAD_SHA in artifact");
          }

          log.info(
              "Context extracted: workflowRunId: {}, headBranch: {}, headSha: {}",
              workflowRunId,
              headBranch,
              headSha);

          return new GitHubWorkflowContext(workflowRunId, headBranch, headSha);
        });
  }
}
