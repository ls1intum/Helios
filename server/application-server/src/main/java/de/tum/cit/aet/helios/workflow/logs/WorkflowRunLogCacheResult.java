package de.tum.cit.aet.helios.workflow.logs;

import de.tum.cit.aet.helios.workflow.WorkflowRun;
import java.nio.file.Path;

record WorkflowRunLogCacheResult(
    WorkflowRun workflowRun,
    Path runDirectory,
    WorkflowRunLogManifest manifest,
    boolean cacheHit) {}
