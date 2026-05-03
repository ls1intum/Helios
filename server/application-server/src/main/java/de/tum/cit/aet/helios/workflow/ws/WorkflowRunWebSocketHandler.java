package de.tum.cit.aet.helios.workflow.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import de.tum.cit.aet.helios.workflow.WorkflowRunDto;
import de.tum.cit.aet.helios.workflow.WorkflowRunService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Log4j2
@Component
@RequiredArgsConstructor
public class WorkflowRunWebSocketHandler extends TextWebSocketHandler
    implements SubProtocolCapable {

  /** Subprotocol the server selects and echoes back; clients must include this. */
  public static final String SUBPROTOCOL = "helios.v1";

  /** Handshake attribute key for the authenticated GitHub username. */
  public static final String ATTR_USERNAME = "helios.username";

  /** Handshake attribute key for the repository id supplied at handshake. */
  public static final String ATTR_REPOSITORY_ID = "helios.repositoryId";

  /** Handshake attribute key marking a developer that bypasses per-repo permission checks. */
  public static final String ATTR_IS_DEVELOPER = "helios.isDeveloper";

  private static final int SEND_BUFFER_LIMIT = 64 * 1024;
  private static final int SEND_TIME_LIMIT_MS = 5_000;
  private static final long JOBS_INVALIDATION_INTERVAL_MS = 10_000;

  private final ObjectMapper objectMapper;
  private final WorkflowRunService workflowRunService;
  private final GitHubService gitHubService;

  private final ConcurrentHashMap<Long, Set<WebSocketSession>> subscribersByRun =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Set<Long>> runsBySession = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Long> lastJobsBroadcastAt = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    WebSocketSession decorated =
        new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);
    sessions.put(session.getId(), decorated);
    runsBySession.put(session.getId(), ConcurrentHashMap.newKeySet());
    log.debug("WS connected: sessionId={}, user={}", session.getId(), getUsername(session));
  }

  @Override
  protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
    WebSocketSession session = sessions.getOrDefault(rawSession.getId(), rawSession);
    WsClientMessage parsed;
    try {
      parsed = objectMapper.readValue(message.getPayload(), WsClientMessage.class);
    } catch (Exception e) {
      log.warn("WS bad message from session {}: {}", session.getId(), e.getMessage());
      sendError(session, "bad-request", "Invalid message", null);
      return;
    }

    switch (parsed) {
      case WsClientMessage.Subscribe sub -> handleSubscribe(session, sub.runId());
      case WsClientMessage.Unsubscribe unsub -> handleUnsubscribe(session, unsub.runId());
      case WsClientMessage.Ping ignored -> send(session, new WsServerMessage.Pong());
    }
  }

  private void handleSubscribe(WebSocketSession session, long runId) {
    Optional<Long> owningRepoOpt = workflowRunService.findOwningRepositoryId(runId);
    if (owningRepoOpt.isEmpty() || owningRepoOpt.get() == null) {
      sendError(session, "not-found", "Workflow run not found", runId);
      return;
    }
    Long owningRepoId = owningRepoOpt.get();

    if (!isAuthorized(session, owningRepoId)) {
      sendError(session, "forbidden", "Not allowed to subscribe to this run", runId);
      return;
    }

    subscribersByRun.computeIfAbsent(runId, k -> ConcurrentHashMap.newKeySet()).add(session);
    runsBySession.computeIfAbsent(session.getId(), k -> ConcurrentHashMap.newKeySet()).add(runId);

    workflowRunService
        .findByIdForRepository(runId, owningRepoId)
        .ifPresent(dto -> send(session, new WsServerMessage.WorkflowRunUpdated(runId, dto)));
    send(session, new WsServerMessage.WorkflowJobsInvalidated(runId));
  }

  private void handleUnsubscribe(WebSocketSession session, long runId) {
    Set<WebSocketSession> subs = subscribersByRun.get(runId);
    if (subs != null) {
      subs.remove(session);
      if (subs.isEmpty()) {
        subscribersByRun.remove(runId, subs);
      }
    }
    Set<Long> runs = runsBySession.get(session.getId());
    if (runs != null) {
      runs.remove(runId);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
    Set<Long> runs = runsBySession.remove(session.getId());
    if (runs != null) {
      for (Long runId : runs) {
        Set<WebSocketSession> subs = subscribersByRun.get(runId);
        if (subs != null) {
          subs.remove(session);
          if (subs.isEmpty()) {
            subscribersByRun.remove(runId, subs);
          }
        }
      }
    }
    log.debug("WS closed: sessionId={}, status={}", session.getId(), status);
  }

  /**
   * Broadcast an update for a single run to every subscribed session. Called by
   * {@code GitHubWorkflowRunSyncService} after persisting a workflow run update.
   */
  public void broadcastRunUpdated(long runId, WorkflowRunDto run) {
    Set<WebSocketSession> subs = subscribersByRun.get(runId);
    if (subs == null || subs.isEmpty()) {
      return;
    }
    WsServerMessage.WorkflowRunUpdated payload =
        new WsServerMessage.WorkflowRunUpdated(runId, run);
    for (WebSocketSession s : subs) {
      send(s, payload);
    }
  }

  /**
   * Notify subscribed clients that workflow jobs for {@code runId} have changed; clients refetch
   * via the existing REST endpoint.
   *
   * <p>Rate-limited: at most one broadcast per runId every
   * {@value #JOBS_INVALIDATION_INTERVAL_MS} ms to avoid excessive GitHub API calls.
   */
  public void broadcastJobsInvalidated(long runId) {
    long now = System.currentTimeMillis();
    Long prev = lastJobsBroadcastAt.put(runId, now);
    if (prev != null && now - prev < JOBS_INVALIDATION_INTERVAL_MS) {
      return;
    }
    Set<WebSocketSession> subs = subscribersByRun.get(runId);
    if (subs == null || subs.isEmpty()) {
      return;
    }
    WsServerMessage.WorkflowJobsInvalidated payload =
        new WsServerMessage.WorkflowJobsInvalidated(runId);
    for (WebSocketSession s : subs) {
      send(s, payload);
    }
  }

  @Scheduled(fixedRate = 25_000)
  public void heartbeat() {
    for (WebSocketSession session : sessions.values()) {
      if (!session.isOpen()) {
        continue;
      }
      try {
        session.sendMessage(new PingMessage());
      } catch (IOException | IllegalStateException e) {
        log.debug("Heartbeat failed for session {}: {}", session.getId(), e.getMessage());
        try {
          session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException ignored) {
          // best-effort close
        }
      }
    }
  }

  private boolean isAuthorized(WebSocketSession session, Long owningRepositoryId) {
    Object connectedRepo = session.getAttributes().get(ATTR_REPOSITORY_ID);
    if (connectedRepo == null || !owningRepositoryId.toString().equals(connectedRepo.toString())) {
      return false;
    }
    Boolean isDeveloper = (Boolean) session.getAttributes().get(ATTR_IS_DEVELOPER);
    if (Boolean.TRUE.equals(isDeveloper)) {
      return true;
    }
    String username = (String) session.getAttributes().get(ATTR_USERNAME);
    if (username == null) {
      return false;
    }
    try {
      GitHubRepositoryRoleDto role =
          gitHubService.getRepositoryRole(owningRepositoryId.toString(), username);
      return role.getPermission() != null
          && role.getPermission() != RepoPermissionType.NONE;
    } catch (IOException e) {
      log.warn(
          "Failed to resolve repo role for user {} on repo {}: {}",
          username,
          owningRepositoryId,
          e.getMessage());
      return false;
    }
  }

  private void send(WebSocketSession session, WsServerMessage payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      session.sendMessage(new TextMessage(json));
    } catch (IOException | IllegalStateException e) {
      log.warn("Failed to send WS message to {}: {}", session.getId(), e.getMessage());
    }
  }

  private void sendError(WebSocketSession session, String code, String message, Long runId) {
    send(session, new WsServerMessage.Error(code, message, runId));
  }

  private static String getUsername(WebSocketSession session) {
    return (String) session.getAttributes().get(ATTR_USERNAME);
  }

  @Override
  public List<String> getSubProtocols() {
    return List.of(SUBPROTOCOL);
  }
}
