package de.tum.cit.aet.helios.workflow.detection;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowDeploymentJobDetectionDto(
    @NonNull Long workflowId,
    @NonNull String workflowPath,
    @NonNull String ref,
    String deploymentJobName,
    @NonNull Status status,
    @NonNull String message) {

  public enum Status {
    FOUND,
    NOT_FOUND,
    UNCLEAR,
    ERROR
  }
}
