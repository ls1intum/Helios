package de.tum.cit.aet.helios.workflow.ws;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = WsClientMessage.Subscribe.class, name = "subscribe"),
  @JsonSubTypes.Type(value = WsClientMessage.Unsubscribe.class, name = "unsubscribe"),
  @JsonSubTypes.Type(value = WsClientMessage.Ping.class, name = "ping"),
})
public sealed interface WsClientMessage {
  record Subscribe(long runId) implements WsClientMessage {}

  record Unsubscribe(long runId) implements WsClientMessage {}

  record Ping() implements WsClientMessage {}
}
