package de.tum.cit.aet.helios.workflow.queue.reconcile;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The {@code @Async} self-invocation bug (PR #1046 follow-up #1) means we can't easily test the
 * full async path from a unit test — Spring's AOP isn't active. These tests pin the {@code
 * running} flag semantics and document that {@code start()} delegates to {@code runAsync()}
 * synchronously today.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowJobBackfillServiceTest {

  @Mock GitRepoRepository repositoryRepository;
  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock GitHubRestClient restClient;
  @InjectMocks WorkflowJobBackfillService service;

  @Test
  void doubleStartReturnsFalseSecondTime() {
    // First start triggers the synchronous walk over (empty) repositoryRepository.findAll();
    // when it completes, running is reset to false.
    boolean firstStarted = service.start();
    boolean secondStarted = service.start();

    assertThat(firstStarted).isTrue();
    // Second start also succeeds because the first run completed synchronously and reset the flag.
    // This is a sentinel for the @Async bug: in the proxied (correct) world, second would be false
    // while first is still running. See PR #1046 follow-up #1.
    assertThat(secondStarted).isTrue();
  }

  @Test
  void isRunningFalseAfterCompletion() {
    service.start();
    assertThat(service.isRunning()).isFalse();
  }
}
