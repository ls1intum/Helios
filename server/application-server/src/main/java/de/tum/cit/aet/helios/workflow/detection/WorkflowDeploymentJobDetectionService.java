package de.tum.cit.aet.helios.workflow.detection;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Log4j2
public class WorkflowDeploymentJobDetectionService {

  private final WorkflowRepository workflowRepository;
  private final GitHubService gitHubService;
  private final WorkflowDeploymentJobDetector workflowDeploymentJobDetector;

  @Transactional(readOnly = true)
  public WorkflowDeploymentJobDetectionDto detectDeploymentJob(Long repositoryId, Long workflowId) {
    Workflow workflow =
        workflowRepository
            .findById(workflowId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId));

    if (workflow.getRepository() == null
        || !workflow.getRepository().getRepositoryId().equals(repositoryId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found in repository");
    }

    String ref = workflow.getRepository().getDefaultBranch();

    try {
      String workflowContent =
          gitHubService.getRepositoryFileContent(
              workflow.getRepository().getNameWithOwner(), workflow.getPath(), ref);

      WorkflowDeploymentJobDetector.DetectionResult detection =
          workflowDeploymentJobDetector.detect(
              workflow.getName(), workflow.getPath(), workflowContent);

      return new WorkflowDeploymentJobDetectionDto(
          workflow.getId(),
          workflow.getPath(),
          ref,
          detection.deploymentJobName(),
          detection.status(),
          detection.message());
    } catch (IOException e) {
      log.warn(
          "Failed to detect deployment job for workflow {} in repository {}: {}",
          workflowId,
          repositoryId,
          e.getMessage());
      return new WorkflowDeploymentJobDetectionDto(
          workflow.getId(),
          workflow.getPath(),
          ref,
          null,
          WorkflowDeploymentJobDetectionDto.Status.ERROR,
          "Helios could not analyze the workflow right now.");
    }
  }
}
