package de.tum.cit.aet.helios.workflow.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.config.WebSocketSessionRegistry;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.workflow.WorkflowRunService;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class WorkflowRunWebSocketHandlerTest {

  private static final long RUN_ID = 42L;

  @Mock private WorkflowRunService workflowRunService;
  @Mock private GitHubService gitHubService;
  @Mock private WebSocketSessionRegistry sessionRegistry;

  private WorkflowRunWebSocketHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new WorkflowRunWebSocketHandler(
            new ObjectMapper(), workflowRunService, gitHubService, sessionRegistry);
  }

  @Test
  void afterConnectionClosedRemovesDecoratedSessionById() {
    WebSocketSession rawSession = session("session-1");
    WebSocketSession decoratedSession = session("session-1");

    subscribersByRun().put(RUN_ID, ConcurrentHashMap.newKeySet());
    subscribersByRun().get(RUN_ID).add(decoratedSession);
    runsBySession().put("session-1", ConcurrentHashMap.newKeySet());
    runsBySession().get("session-1").add(RUN_ID);

    handler.afterConnectionClosed(rawSession, CloseStatus.NORMAL);

    verify(sessionRegistry).unregister("session-1");
    assertFalse(runsBySession().containsKey("session-1"));
    assertFalse(subscribersByRun().containsKey(RUN_ID));
  }

  @Test
  void broadcastJobsInvalidatedKeepsThrottleTimestampForSuppressedEvent() {
    WebSocketSession session = session("session-1");
    subscribersByRun().put(RUN_ID, ConcurrentHashMap.newKeySet());
    subscribersByRun().get(RUN_ID).add(session);
    lastJobsBroadcastAt().put(RUN_ID, System.currentTimeMillis());

    Long previousTimestamp = lastJobsBroadcastAt().get(RUN_ID);

    handler.broadcastJobsInvalidated(RUN_ID);

    assertEquals(previousTimestamp, lastJobsBroadcastAt().get(RUN_ID));
    verify(sessionRegistry, never()).send(eq(session), any(WsServerMessage.class));
  }

  @Test
  void broadcastJobsInvalidatedSendsAndAdvancesTimestampAfterThrottleWindow() {
    WebSocketSession session = session("session-1");
    subscribersByRun().put(RUN_ID, ConcurrentHashMap.newKeySet());
    subscribersByRun().get(RUN_ID).add(session);
    lastJobsBroadcastAt().put(RUN_ID, System.currentTimeMillis() - 11_000);

    Long previousTimestamp = lastJobsBroadcastAt().get(RUN_ID);

    handler.broadcastJobsInvalidated(RUN_ID);

    verify(sessionRegistry).send(session, new WsServerMessage.WorkflowJobsInvalidated(RUN_ID));
    assertTrue(lastJobsBroadcastAt().get(RUN_ID) > previousTimestamp);
  }

  @Test
  void broadcastJobsInvalidatedDoesNotThrottleRunsWithoutSubscribers() {
    handler.broadcastJobsInvalidated(RUN_ID);

    assertNull(lastJobsBroadcastAt().get(RUN_ID));
    verify(sessionRegistry, never()).send(any(WebSocketSession.class), any(WsServerMessage.class));
  }

  private static WebSocketSession session(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    lenient().when(session.getId()).thenReturn(id);
    return session;
  }

  @SuppressWarnings("unchecked")
  private ConcurrentHashMap<Long, Set<WebSocketSession>> subscribersByRun() {
    return (ConcurrentHashMap<Long, Set<WebSocketSession>>)
        ReflectionTestUtils.getField(handler, "subscribersByRun");
  }

  @SuppressWarnings("unchecked")
  private ConcurrentHashMap<String, Set<Long>> runsBySession() {
    return (ConcurrentHashMap<String, Set<Long>>)
        ReflectionTestUtils.getField(handler, "runsBySession");
  }

  @SuppressWarnings("unchecked")
  private ConcurrentHashMap<Long, Long> lastJobsBroadcastAt() {
    return (ConcurrentHashMap<Long, Long>)
        ReflectionTestUtils.getField(handler, "lastJobsBroadcastAt");
  }
}
