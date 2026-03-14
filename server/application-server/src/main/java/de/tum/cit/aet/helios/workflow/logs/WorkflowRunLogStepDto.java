package de.tum.cit.aet.helios.workflow.logs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record WorkflowRunLogStepDto(
    @Schema(description = "The GitHub step number when available") Integer number,
    @Schema(description = "The GitHub step name when available") String name,
    @Schema(description = "The GitHub step status when available") String status,
    @Schema(description = "The GitHub step conclusion when available") String conclusion,
    @Schema(description = "The GitHub step start timestamp when available")
        OffsetDateTime startedAt,
    @Schema(description = "The GitHub step completion timestamp when available")
        OffsetDateTime completedAt) {}
