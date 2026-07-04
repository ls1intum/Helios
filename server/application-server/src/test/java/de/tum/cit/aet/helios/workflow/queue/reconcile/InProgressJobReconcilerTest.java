package de.tum.cit.aet.helios.workflow.queue.reconcile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InProgressJobReconcilerTest {

  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock GitRepoRepository repositoryRepository;
  @Mock GitHubRestClient restClient;
  @InjectMocks InProgressJobReconciler reconciler;

  @Test
  void noJobsToReconcileIsNoop() {
    when(workflowJobRepository.findJobsNeedingRunnerReconciliation(any(), any()))
        .thenReturn(List.of());

    reconciler.reconcile();

    verify(workflowJobRepository, org.mockito.Mockito.never())
        .touchReconcileAttempt(anyList(), any());
  }

  @Test
  void backoffPreventsRepeatedAttemptsOnSameJob() {
    // After backoff filter returns no jobs needing reconciliation, no REST call happens.
    when(workflowJobRepository.findJobsNeedingRunnerReconciliation(any(), any()))
        .thenReturn(List.of());

    reconciler.reconcile();
    reconciler.reconcile();
    reconciler.reconcile();

    verify(restClient, org.mockito.Mockito.never()).get(any());
  }
}
