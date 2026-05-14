package de.tum.cit.aet.helios.environment.ws;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EnvironmentDeploymentWebSocketMessage.Ping.class, name = "ping"),
})
public sealed interface EnvironmentDeploymentWebSocketMessage {

  record Ping() implements EnvironmentDeploymentWebSocketMessage {}

  @JsonTypeName("environment-deployment-invalidated")
  record EnvironmentDeploymentInvalidated(long repositoryId, long environmentId)
      implements EnvironmentDeploymentWebSocketMessage {}

  @JsonTypeName("error")
  record Error(String code, String message) implements EnvironmentDeploymentWebSocketMessage {}

  @JsonTypeName("pong")
  record Pong() implements EnvironmentDeploymentWebSocketMessage {}
}
