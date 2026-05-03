package de.tum.cit.aet.helios.workflow.ws;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import de.tum.cit.aet.helios.workflow.WorkflowRunDto;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface WsServerMessage {

  @JsonTypeName("workflow-run-updated")
  record WorkflowRunUpdated(long runId, WorkflowRunDto run) implements WsServerMessage {}

  /**
   * Tells the client that workflow jobs for {@code runId} have changed. The client refetches via
   * the existing REST endpoint — jobs are not persisted server-side, so we don't include them in
   * the push payload.
   */
  @JsonTypeName("workflow-jobs-invalidated")
  record WorkflowJobsInvalidated(long runId) implements WsServerMessage {}

  @JsonTypeName("error")
  record Error(String code, String message, Long runId) implements WsServerMessage {}

  @JsonTypeName("pong")
  record Pong() implements WsServerMessage {}
}
