package de.tum.cit.aet.helios.workflow.logs.storage;

import de.tum.cit.aet.helios.workflow.WorkflowRun;
import java.nio.file.Path;

public record WorkflowRunLogCacheResult(
    WorkflowRun workflowRun,
    Path runDirectory,
    WorkflowRunLogManifest manifest,
    boolean cacheHit) {}
