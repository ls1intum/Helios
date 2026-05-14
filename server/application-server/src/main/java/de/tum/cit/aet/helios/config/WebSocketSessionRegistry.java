package de.tum.cit.aet.helios.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Centralized registry for WebSocket sessions across all Helios handlers.
 *
 * <p>Owns session decoration, heartbeats, JSON serialization, and consecutive-send-failure
 * eviction. Handlers register raw sessions on connect, use the returned decorated session for all
 * subsequent operations, and unregister on close.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class WebSocketSessionRegistry {

  private static final int SEND_BUFFER_LIMIT = 64 * 1024;
  private static final int SEND_TIME_LIMIT_MS = 5_000;
  private static final int MAX_CONSECUTIVE_SEND_FAILURES = 3;
  private static final long HEARTBEAT_INTERVAL_MS = 25_000;

  private final ObjectMapper objectMapper;

  private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicInteger> sendFailures = new ConcurrentHashMap<>();

  /**
   * Wrap the raw session in a {@link ConcurrentWebSocketSessionDecorator} and start tracking it.
   * Callers must use the returned decorated session for all sends.
   */
  public WebSocketSession register(WebSocketSession rawSession) {
    WebSocketSession decorated =
        new ConcurrentWebSocketSessionDecorator(rawSession, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);
    sessions.put(rawSession.getId(), decorated);
    sendFailures.put(rawSession.getId(), new AtomicInteger(0));
    return decorated;
  }

  /** Look up the decorated session for a raw session id, or {@code null} if unknown. */
  public WebSocketSession get(String sessionId) {
    return sessions.get(sessionId);
  }

  /** Stop tracking the session. Idempotent. */
  public void unregister(String sessionId) {
    sessions.remove(sessionId);
    sendFailures.remove(sessionId);
  }

  /**
   * Serialize {@code payload} as JSON and send it. Resets the per-session failure counter on
   * success. On failure, increments the counter and force-closes the session once it reaches
   * {@value #MAX_CONSECUTIVE_SEND_FAILURES} so a stuck client cannot leak resources indefinitely.
   */
  public void send(WebSocketSession session, Object payload) {
    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      log.warn(
          "Failed to serialize WS payload for session {}: {}", session.getId(), e.getMessage());
      return;
    }
    try {
      session.sendMessage(new TextMessage(json));
      AtomicInteger failures = sendFailures.get(session.getId());
      if (failures != null) {
        failures.set(0);
      }
    } catch (IOException | IllegalStateException e) {
      log.warn("Failed to send WS message to {}: {}", session.getId(), e.getMessage());
      recordFailure(session);
    }
  }

  private void recordFailure(WebSocketSession session) {
    AtomicInteger failures = sendFailures.get(session.getId());
    if (failures != null && failures.incrementAndGet() >= MAX_CONSECUTIVE_SEND_FAILURES) {
      log.warn(
          "Closing WS session {} after {} consecutive send failures",
          session.getId(),
          MAX_CONSECUTIVE_SEND_FAILURES);
      closeQuietly(session, CloseStatus.PROTOCOL_ERROR);
    }
  }

  /**
   * Periodic ping to detect dead TCP connections. Sessions whose ping fails are force-closed; the
   * handler's {@code afterConnectionClosed} is invoked by Spring and handles handler-specific
   * cleanup, while {@link #unregister(String)} removes the entry from this registry.
   */
  @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
  public void heartbeat() {
    for (WebSocketSession session : sessions.values()) {
      if (!session.isOpen()) {
        continue;
      }
      try {
        session.sendMessage(new PingMessage());
      } catch (IOException | IllegalStateException e) {
        log.debug("WS heartbeat failed for session {}: {}", session.getId(), e.getMessage());
        closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
      }
    }
  }

  private static void closeQuietly(WebSocketSession session, CloseStatus status) {
    try {
      session.close(status);
    } catch (IOException ignored) {
      // best-effort
    }
  }
}
