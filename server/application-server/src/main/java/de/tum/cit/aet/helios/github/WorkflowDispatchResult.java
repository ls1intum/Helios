package de.tum.cit.aet.helios.github;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkflowDispatchResult(
    @JsonProperty("workflow_run_id") Long workflowRunId,
    @JsonProperty("run_url") String runUrl,
    @JsonProperty("html_url") String htmlUrl) {

  public static WorkflowDispatchResult empty() {
    return new WorkflowDispatchResult(null, null, null);
  }
}
