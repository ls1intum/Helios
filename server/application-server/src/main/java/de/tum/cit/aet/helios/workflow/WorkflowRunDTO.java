package de.tum.cit.aet.helios.workflow;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.helios.workflow.WorkflowRun.Conclusion;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Status;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkflowRunDTO(
		@NonNull Long id,
		@NonNull String name,
		@NonNull String displayTitle,
		@NonNull Status status,
		Conclusion conclusion,
		@NonNull String htmlUrl
) {
	public static WorkflowRunDTO fromWorkflowRun(WorkflowRun run) {
		return new WorkflowRunDTO(
                run.getId(),
				run.getName(),
				run.getDisplayTitle(),
				run.getStatus(),
				run.getConclusion().orElse(null),
				run.getHtmlUrl()
		);
	}
}
