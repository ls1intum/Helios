package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.workflow.WorkflowRun;
import java.util.List;
import org.springframework.lang.Nullable;

public record EnvironmentDeploymentReadinessDto(
    Status status,
    List<RequiredWorkflowStatusDto> workflows) {

  public enum Status {
    UNCONFIGURED,
    READY,
    WAITING,
    FAILED,
    MISSING_RUN
  }

  public enum WorkflowStatus {
    READY,
    WAITING,
    FAILED,
    MISSING_RUN
  }

  public record RequiredWorkflowStatusDto(
      Long workflowId,
      String workflowName,
      WorkflowStatus status,
      @Nullable Long runId,
      @Nullable String runHtmlUrl,
      @Nullable WorkflowRun.Status runStatus,
      @Nullable WorkflowRun.Conclusion runConclusion) {
  }
}
