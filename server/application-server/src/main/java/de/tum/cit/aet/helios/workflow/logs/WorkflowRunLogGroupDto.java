package de.tum.cit.aet.helios.workflow.logs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.lang.NonNull;

public record WorkflowRunLogGroupDto(
    @Schema(
        description =
            "The name of the log group, usually derived from the top-level archive directory")
    @NonNull String name,

    @Schema(description = "The exact GitHub job name matched to this log group when available")
        String jobName,

    @Schema(description = "The GitHub job status matched to this log group when available")
        String jobStatus,

    @Schema(description = "The GitHub job conclusion matched to this log group when available")
        String jobConclusion,

    @Schema(description = "The GitHub job steps matched to this log group when available")
    @NonNull List<WorkflowRunLogStepDto> steps,

    @Schema(description = "The log files belonging to this group")
    @NonNull List<WorkflowRunLogFileDto> files) {}
