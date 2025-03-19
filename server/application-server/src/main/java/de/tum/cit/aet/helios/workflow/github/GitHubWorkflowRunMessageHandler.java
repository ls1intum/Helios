package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import de.tum.cit.aet.helios.tests.TestResultProcessor;
import de.tum.cit.aet.helios.workflow.GitHubWorkflowContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class GitHubWorkflowRunMessageHandler
    extends GitHubMessageHandler<GHEventPayload.WorkflowRun> {
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubWorkflowRunSyncService workflowSyncService;
  private final TestResultProcessor testResultProcessor;
  private final GitHubFacade gitHub;

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
        "Received worfklow run event for repository: {}, workflow run: {}, action: {}, event: {}",
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

    GitHubWorkflowContext context = null;

    // Check if this is a workflow_run event
    if ("workflow_run".equalsIgnoreCase(githubEvent.name())) {
      log.info("Trying to find the triggering workflow run ID of: {}", githubRun.getId());
      context = extractWorkflowContext(repository.getFullName(), githubRun.getId());
      if (context == null) {
        log.error("Failed to extract workflow context for workflow run: {}", githubRun.getId());
        return;
      }
    }

    var run = workflowSyncService.processRun(githubRun);

    if (context != null) {
      run.setTriggeredWorkflowRunId(context.runId());
      run.setHeadBranch(context.headBranch());
      run.setHeadSha(context.headSha());
    }


    if (run != null && testResultProcessor.shouldProcess(run)) {
      testResultProcessor.processRun(run);
    }
  }

  /**
   * Extracts workflow context from the workflow-context artifact.
   *
   * @param repositoryFullName The full name of the repository
   * @param runId The ID of the workflow run
   * @return The extracted GitHubWorkflowContext or null if not found or error occurred
   */
  private GitHubWorkflowContext extractWorkflowContext(String repositoryFullName, long runId) {
    try {
      // Fetch artifacts to get the triggering workflow run ID
      List<GHArtifact> artifacts = gitHub
          .getRepository(repositoryFullName)
          .getWorkflowRun(runId)
          .listArtifacts()
          .toList();

      // Look for the "workflow-context" artifact
      Optional<GHArtifact> contextArtifact = artifacts.stream()
          .filter(a -> "workflow-context".equalsIgnoreCase(a.getName()))
          .findFirst();

      if (contextArtifact.isPresent()) {
        try {
          // Parse the artifact to extract the triggering workflow information
          return parseWorkflowContextArtifact(contextArtifact.get());
        } catch (Exception e) {
          log.error("Failed to parse workflow context artifact: {}", e.getMessage());
          return null;
        }
      } else {
        log.error("No workflow-context artifact found for E2E Tests workflow_run: {}", runId);
        return null;
      }
    } catch (IOException e) {
      log.error("Failed to fetch artifacts for workflow run: {}", e.getMessage());
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
                // Read file content
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipInput))) {
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

          return new GitHubWorkflowContext(workflowRunId, headBranch, headSha);
        });
  }
}
