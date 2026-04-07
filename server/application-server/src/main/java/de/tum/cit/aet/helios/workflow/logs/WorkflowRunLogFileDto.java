package de.tum.cit.aet.helios.workflow.logs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

public record WorkflowRunLogFileDto(
    @Schema(description = "The relative path of the log file inside the workflow log archive")
    @NonNull String path,

    @Schema(description = "A display name for the log file")
    @NonNull String displayName,

    @Schema(description = "The GitHub step number matched to this log file when available")
        Integer stepNumber,

    @Schema(description = "The GitHub step name matched to this log file when available")
        String stepName,

    @Schema(description = "The GitHub step status matched to this log file when available")
        String stepStatus,

    @Schema(
        description =
            "The GitHub step conclusion matched to this log file when available")
        String stepConclusion,

    @Schema(
        description = "The GitHub step start timestamp matched to this log file when available")
        OffsetDateTime stepStartedAt,

    @Schema(
        description =
            "The GitHub step completion timestamp matched to this log file when available")
        OffsetDateTime stepCompletedAt,

    @Schema(description = "The processed text content of the log file")
    @NonNull String content) {}
