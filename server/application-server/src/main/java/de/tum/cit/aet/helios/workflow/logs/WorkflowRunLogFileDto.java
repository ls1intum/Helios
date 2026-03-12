package de.tum.cit.aet.helios.workflow.logs;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

public record WorkflowRunLogFileDto(
    @Schema(description = "The relative path of the log file inside the workflow log archive")
    @NonNull String path,

    @Schema(description = "A display name for the log file")
    @NonNull String displayName,

    @Schema(description = "The processed text content of the log file")
    @NonNull String content) {}
