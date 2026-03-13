package de.tum.cit.aet.helios.workflow.logs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.lang.NonNull;

public record WorkflowRunLogGroupDto(
    @Schema(
        description =
            "The name of the log group, usually derived from the top-level archive directory")
    @NonNull String name,

    @Schema(description = "The log files belonging to this group")
    @NonNull List<WorkflowRunLogFileDto> files) {}
