package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.workflow.WorkflowRun.Conclusion;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Status;
import org.springframework.lang.NonNull;

public record WorkflowRunDto(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String displayTitle,
    @NonNull Status status,
    @NonNull Long workflowId,
    Conclusion conclusion,
    @NonNull String htmlUrl,
    @NonNull Workflow.Label label,
    WorkflowRun.TestProcessingStatus testProcessingStatus) {
  public static WorkflowRunDto fromWorkflowRun(WorkflowRun run) {
    return new WorkflowRunDto(
        run.getId(),
        run.getName(),
        run.getDisplayTitle(),
        run.getStatus(),
        run.getWorkflow().getId(),
        run.getConclusion().orElse(null),
        run.getHtmlUrl(),
        run.getWorkflow().getLabel(),
        run.getTestProcessingStatus());
  }
}
