package de.tum.cit.aet.helios.workflow.github;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.workflow.queue.QueueIndexService;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobPersistenceService;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression contract from plan §B2 / §H:
 *
 * <ol>
 *   <li>Existing {@code persistDurations} path is called first and behaves identically.
 *   <li>New {@code upsert} and {@code queueIndex} calls happen after.
 *   <li>If either new call throws, the handler still returns normally (no NATS redelivery loop).
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class GitHubWorkflowJobMessageHandlerTest {

  @Mock GitHubService gitHubService;
  @Mock GitHubWorkflowJobTimingService timingService;
  @Mock WorkflowJobPersistenceService persistenceService;
  @Mock QueueIndexService queueIndexService;

  @InjectMocks GitHubWorkflowJobMessageHandler handler;

  private GitHubWorkflowJobPayload payload() {
    GitHubWorkflowJobPayload.WorkflowJob job = new GitHubWorkflowJobPayload.WorkflowJob(
        42L, 99L, "CI", "main", "abc123", "https://x", "queued", null,
        OffsetDateTime.parse("2026-05-18T10:00:00Z"), null, null, "build",
        List.of("self-hosted", "linux"), null, null, null, null);
    return new GitHubWorkflowJobPayload(
        "queued", job, null,
        new GitHubWorkflowJobPayload.Repository(7L, "ls1intum/Helios"));
  }

  private void invokeHandleMessage(GitHubWorkflowJobPayload payload) throws Exception {
    Method m = GitHubWorkflowJobMessageHandler.class.getDeclaredMethod("handleMessage",
        GitHubWorkflowJobPayload.class);
    m.setAccessible(true);
    m.invoke(handler, payload);
  }

  @Test
  void happyPathCallsAllThreeServicesInOrder() throws Exception {
    when(gitHubService.getInstalledRepositories()).thenReturn(List.of("ls1intum/Helios"));

    invokeHandleMessage(payload());

    InOrder order = inOrder(timingService, persistenceService, queueIndexService);
    order.verify(timingService).persistDurations(any());
    order.verify(persistenceService).upsert(any());
    order.verify(queueIndexService).onWorkflowJobEvent(any());
  }

  @Test
  void persistenceFailureDoesNotBreakDeploymentTimingPath() throws Exception {
    when(gitHubService.getInstalledRepositories()).thenReturn(List.of("ls1intum/Helios"));
    doThrow(new RuntimeException("db down")).when(persistenceService).upsert(any());

    // Must NOT throw — exception is swallowed so NATS does not redeliver.
    invokeHandleMessage(payload());

    verify(timingService, times(1)).persistDurations(any());
    verify(persistenceService, times(1)).upsert(any());
    verify(queueIndexService, times(1)).onWorkflowJobEvent(any());
  }

  @Test
  void queueIndexFailureDoesNotBreakOtherPaths() throws Exception {
    when(gitHubService.getInstalledRepositories()).thenReturn(List.of("ls1intum/Helios"));
    doThrow(new RuntimeException("cache exploded")).when(queueIndexService).onWorkflowJobEvent(any());

    invokeHandleMessage(payload());

    verify(timingService).persistDurations(any());
    verify(persistenceService).upsert(any());
    verify(queueIndexService).onWorkflowJobEvent(any());
  }

  @Test
  void skipsIfRepositoryNotInstalled() throws Exception {
    when(gitHubService.getInstalledRepositories()).thenReturn(List.of("someone-else/Repo"));

    invokeHandleMessage(payload());

    verify(timingService, times(0)).persistDurations(any());
    verify(persistenceService, times(0)).upsert(any());
    verify(queueIndexService, times(0)).onWorkflowJobEvent(any());
  }
}
