package de.tum.cit.aet.helios.workflow.detection;

import de.tum.cit.aet.helios.ai.AiProperties;
import de.tum.cit.aet.helios.ai.AiProvider;
import de.tum.cit.aet.helios.ai.AiProviderRegistry;
import de.tum.cit.aet.helios.ai.AiTextUtils;
import java.io.IOException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class OpenAiWorkflowDeploymentJobDetector implements WorkflowDeploymentJobDetector {
  private static final int MAX_WORKFLOW_CONTENT_CHARS = 30_000;

  private final AiProperties aiProperties;
  private final AiProviderRegistry providerRegistry;

  @Override
  public DetectionResult detect(String workflowName, String workflowPath, String workflowContent)
      throws IOException {
    if (!aiProperties.isEnabled()) {
      return new DetectionResult(
          null,
          WorkflowDeploymentJobDetectionDto.Status.ERROR,
          "AI detection is not configured on the server.");
    }

    try {
      String providerId =
          aiProperties.getDefaultProvider() == null || aiProperties.getDefaultProvider().isBlank()
              ? "openai"
              : aiProperties.getDefaultProvider();
      AiProvider provider = providerRegistry.resolve(providerId);
      DetectionSchema schema =
          provider.call(
              buildSystemPrompt(), buildUserPrompt(workflowName, workflowPath, workflowContent),
              DetectionSchema.class);

      WorkflowDeploymentJobDetectionDto.Status status =
          WorkflowDeploymentJobDetectionDto.Status.valueOf(
              schema.status().trim().toUpperCase(Locale.ROOT));
      String deploymentJobName =
          schema.deploymentJobName() == null || schema.deploymentJobName().isBlank()
              ? null
              : schema.deploymentJobName();
      log.info(
          "Spring AI deployment-job detection response for workflow '{}' ({}): "
              + "status={}, deploymentJobName={}, message={}",
          workflowName,
          workflowPath,
          status,
          deploymentJobName,
          schema.message());
      return new DetectionResult(deploymentJobName, status, schema.message());
    } catch (Exception e) {
      log.warn("Spring AI deployment-job detection failed: {}", e.getMessage());
      return new DetectionResult(
          null,
          WorkflowDeploymentJobDetectionDto.Status.ERROR,
          "AI detection failed to analyze the workflow.");
    }
  }

  private String buildSystemPrompt() {
    return """
        You analyze GitHub Actions workflow YAML and identify the single deployment job.
        Prefer the job's explicit `name` when it exists.
        Otherwise return the GitHub Actions job key.
        Return status NOT_FOUND when there is no deployment job.
        Return status UNCLEAR when multiple jobs look like deployment jobs.
        Use UNCLEAR if you cannot confidently choose one.
        Return an empty deploymentJobName when the status is not FOUND.
        Keep the message short and factual.
        """;
  }

  private String buildUserPrompt(String workflowName, String workflowPath, String workflowContent) {
    return """
        Workflow name: %s
        Workflow path: %s

        Workflow YAML:
        ```yaml
        %s
        ```
        """
        .formatted(
            workflowName,
            workflowPath,
            AiTextUtils.truncate(workflowContent, MAX_WORKFLOW_CONTENT_CHARS));
  }

  private record DetectionSchema(String deploymentJobName, String status, String message) {}
}
