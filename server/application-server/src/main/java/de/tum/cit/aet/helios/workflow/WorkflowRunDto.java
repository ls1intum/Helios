package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.workflow.WorkflowRun.Conclusion;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Status;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public record WorkflowRunDto(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String displayTitle,
    @NonNull Status status,
    @NonNull Long workflowId,
    Conclusion conclusion,
    @NonNull String htmlUrl,
    @NonNull Workflow.Label label,
    @Nullable WorkflowRun.TestProcessingStatus testProcessingStatus,
    @Nullable String headBranch,
    @Nullable String headSha,
    @Nullable OffsetDateTime runStartedAt,
    @NonNull OffsetDateTime createdAt,
    @NonNull OffsetDateTime updatedAt) {
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
        run.getTestProcessingStatus(),
        run.getHeadBranch(),
        run.getHeadSha(),
        run.getRunStartedAt(),
        run.getCreatedAt(),
        run.getUpdatedAt());
  }
}
