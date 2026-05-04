package de.tum.cit.aet.helios.environment.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.config.WebSocketSessionAttributes;
import de.tum.cit.aet.helios.config.WebSocketSessionRegistry;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Log4j2
@Component
@RequiredArgsConstructor
public class EnvironmentDeploymentWebSocketHandler extends TextWebSocketHandler
    implements SubProtocolCapable {

  public static final String SUBPROTOCOL = "helios.v1";

  private final ObjectMapper objectMapper;
  private final GitHubService gitHubService;
  private final WebSocketSessionRegistry sessionRegistry;

  private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByRepository =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> repositoryBySession = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws IOException {
    Long repositoryId = getRepositoryId(session);
    if (repositoryId == null || !isAuthorized(session, repositoryId)) {
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }

    WebSocketSession decorated = sessionRegistry.register(session);
    repositoryBySession.put(session.getId(), repositoryId);
    sessionsByRepository
        .computeIfAbsent(repositoryId, ignored -> ConcurrentHashMap.newKeySet())
        .add(decorated);

    log.debug(
        "Environment deployment WS connected: sessionId={}, repositoryId={}, user={}",
        session.getId(),
        repositoryId,
        getUsername(session));
  }

  @Override
  protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
    WebSocketSession session = sessionRegistry.get(rawSession.getId());
    if (session == null) {
      session = rawSession;
    }
    EnvironmentDeploymentWebSocketMessage parsed;
    try {
      parsed =
          objectMapper.readValue(
              message.getPayload(), EnvironmentDeploymentWebSocketMessage.class);
    } catch (Exception e) {
      log.warn(
          "Environment deployment WS bad message from {}: {}", session.getId(), e.getMessage());
      sessionRegistry.send(
          session,
          new EnvironmentDeploymentWebSocketMessage.Error("bad-request", "Invalid message"));
      return;
    }

    switch (parsed) {
      case EnvironmentDeploymentWebSocketMessage.Ping ignored ->
          sessionRegistry.send(session, new EnvironmentDeploymentWebSocketMessage.Pong());
      default ->
          sessionRegistry.send(
              session,
              new EnvironmentDeploymentWebSocketMessage.Error(
                  "bad-request", "Unsupported message"));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessionRegistry.unregister(session.getId());
    Long repositoryId = repositoryBySession.remove(session.getId());
    if (repositoryId != null) {
      Set<WebSocketSession> repositorySessions = sessionsByRepository.get(repositoryId);
      if (repositorySessions != null) {
        repositorySessions.removeIf(existing -> existing.getId().equals(session.getId()));
        if (repositorySessions.isEmpty()) {
          sessionsByRepository.remove(repositoryId, repositorySessions);
        }
      }
    }
    log.debug("Environment deployment WS closed: sessionId={}, status={}", session.getId(), status);
  }

  public void broadcastDeploymentInvalidated(long repositoryId, long environmentId) {
    Set<WebSocketSession> repositorySessions = sessionsByRepository.get(repositoryId);
    if (repositorySessions == null || repositorySessions.isEmpty()) {
      return;
    }

    EnvironmentDeploymentWebSocketMessage.EnvironmentDeploymentInvalidated payload =
        new EnvironmentDeploymentWebSocketMessage.EnvironmentDeploymentInvalidated(
            repositoryId, environmentId);
    for (WebSocketSession session : repositorySessions) {
      sessionRegistry.send(session, payload);
    }
  }

  private boolean isAuthorized(WebSocketSession session, Long repositoryId) {
    Boolean isDeveloper =
        (Boolean) session.getAttributes().get(WebSocketSessionAttributes.IS_DEVELOPER);
    if (Boolean.TRUE.equals(isDeveloper)) {
      return true;
    }

    String username = getUsername(session);
    if (username == null) {
      return false;
    }

    try {
      GitHubRepositoryRoleDto role =
          gitHubService.getRepositoryRole(repositoryId.toString(), username);
      return role.getPermission() != null
          && role.getPermission() != RepoPermissionType.NONE;
    } catch (IOException e) {
      log.warn(
          "Failed to resolve repo role for user {} on repo {}: {}",
          username,
          repositoryId,
          e.getMessage());
      return false;
    }
  }

  private static Long getRepositoryId(WebSocketSession session) {
    Object repositoryId = session.getAttributes().get(WebSocketSessionAttributes.REPOSITORY_ID);
    if (repositoryId == null) {
      return null;
    }
    try {
      return Long.parseLong(repositoryId.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String getUsername(WebSocketSession session) {
    return (String) session.getAttributes().get(WebSocketSessionAttributes.USERNAME);
  }

  @Override
  public List<String> getSubProtocols() {
    return List.of(SUBPROTOCOL);
  }
}
