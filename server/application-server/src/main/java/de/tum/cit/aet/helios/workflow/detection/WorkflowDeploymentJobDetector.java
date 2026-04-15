package de.tum.cit.aet.helios.workflow.detection;

import java.io.IOException;

public interface WorkflowDeploymentJobDetector {

  DetectionResult detect(String workflowName, String workflowPath, String workflowContent)
      throws IOException;

  record DetectionResult(
      String deploymentJobName, WorkflowDeploymentJobDetectionDto.Status status, String message) {}
}
