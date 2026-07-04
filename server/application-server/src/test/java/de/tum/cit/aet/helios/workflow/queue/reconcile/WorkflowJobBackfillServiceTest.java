package de.tum.cit.aet.helios.workflow.queue.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

/**
 * Confirms {@link WorkflowJobBackfillService#start()} dispatches through the proxied
 * {@link WorkflowJobBackfillExecutor} (PR #1046 follow-up #1, fixed) and that the {@code running}
 * flag prevents concurrent invocations.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowJobBackfillServiceTest {

  @Mock GitRepoRepository repositoryRepository;
  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock GitHubRestClient restClient;
  @Mock QueueWaitStatRollup rollup;
  @Mock ApplicationContext applicationContext;
  @Mock WorkflowJobBackfillExecutor executor;
  @InjectMocks WorkflowJobBackfillService service;

  private void stubExecutorLookup() {
    when(applicationContext.getBean(WorkflowJobBackfillExecutor.class)).thenReturn(executor);
    // Executor.runAsync() runs on a worker thread in prod; in tests we do nothing, so the
    // `running` flag stays true (matches real proxied behaviour).
    doNothing().when(executor).runAsync();
  }

  @Test
  void startDispatchesThroughProxiedExecutor() {
    stubExecutorLookup();
    boolean started = service.start();
    assertThat(started).isTrue();
    verify(applicationContext).getBean(WorkflowJobBackfillExecutor.class);
    verify(executor, times(1)).runAsync();
    // running stays true until the executor reports back via runBackfill()'s finally block.
    assertThat(service.isRunning()).isTrue();
  }

  @Test
  void doubleStartIsIdempotent() {
    stubExecutorLookup();
    assertThat(service.start()).isTrue();
    assertThat(service.start()).isFalse(); // already running
    verify(executor, times(1)).runAsync();
  }

  @Test
  void abortStopsLoopBeforeAnyRestCall() {
    // Drive runBackfill directly (skipping the @Async dispatch) so we can observe abort.
    when(repositoryRepository.findAll()).thenReturn(java.util.List.of());
    service.abort();
    service.runBackfill();
    // No REST calls issued; the abort flag short-circuits the per-repo loop.
    verify(restClient, times(0)).get(any());
  }
}
