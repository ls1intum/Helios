package de.tum.cit.aet.helios.workflow.logs;

import java.time.OffsetDateTime;

public record WorkflowRunLogManifest(
    Long workflowRunId, Long repositoryId, OffsetDateTime downloadedAt, int fileCount) {}
