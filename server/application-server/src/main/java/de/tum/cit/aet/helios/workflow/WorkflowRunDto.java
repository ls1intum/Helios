package de.tum.cit.aet.helios.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Conclusion;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Status;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkflowRunDto(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String displayTitle,
    @NonNull Status status,
    Conclusion conclusion,
    @NonNull String htmlUrl) {
  public static WorkflowRunDto fromWorkflowRun(WorkflowRun run) {
    return new WorkflowRunDto(
        run.getId(),
        run.getName(),
        run.getDisplayTitle(),
        run.getStatus(),
        run.getConclusion().orElse(null),
        run.getHtmlUrl());
  }
}
