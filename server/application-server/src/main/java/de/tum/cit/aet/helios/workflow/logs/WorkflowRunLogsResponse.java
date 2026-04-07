package de.tum.cit.aet.helios.workflow.logs;

import de.tum.cit.aet.helios.workflow.WorkflowRun.Conclusion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public record WorkflowRunLogsResponse(
    @Schema(description = "The workflow run identifier")
    @NonNull Long workflowRunId,

    @Schema(description = "The workflow run name", example = "deploy")
    @NonNull String workflowName,

    @Schema(description = "The workflow run display title")
    String displayTitle,

    @Schema(description = "The workflow run conclusion when available")
    Conclusion conclusion,

    @Schema(description = "The HTML URL of the workflow run on GitHub")
    String htmlUrl,

    @Schema(description = "Whether the workflow logs were already cached before this request")
    boolean cacheHit,

    @Schema(description = "When the workflow logs were downloaded and stored")
    @NonNull OffsetDateTime downloadedAt,

    @Schema(description = "The number of extracted log files")
    int totalFileCount,

    @Schema(description = "The processed log groups")
    @NonNull List<WorkflowRunLogGroupDto> groups) {}
