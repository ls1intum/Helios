package de.tum.cit.aet.helios.workflow.logs.storage;

import java.time.OffsetDateTime;

public record WorkflowRunLogManifest(
    Long workflowRunId,
    Long repositoryId,
    OffsetDateTime downloadedAt,
    int fileCount,
    Long runAttempt) {

  public WorkflowRunLogManifest(
      Long workflowRunId, Long repositoryId, OffsetDateTime downloadedAt, int fileCount) {
    this(workflowRunId, repositoryId, downloadedAt, fileCount, null);
  }

  public static final String FILE_NAME = "_manifest.json";
}
