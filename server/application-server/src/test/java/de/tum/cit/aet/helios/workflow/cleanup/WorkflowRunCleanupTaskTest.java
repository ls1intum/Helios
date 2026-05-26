package de.tum.cit.aet.helios.workflow.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class WorkflowRunCleanupTaskTest {

  private static final int BATCH_SIZE = 5000;

  @Test
  void purgeOrphanBranchRunsDeletesInBatchesUntilDrained() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);
    // First two batches return a full batch → loop continues; third returns a
    // short batch → loop terminates with 5000 + 5000 + 273 = 10 273 total.
    when(repo.purgeOrphanBranchRunsBatch(7, BATCH_SIZE))
        .thenReturn(BATCH_SIZE)
        .thenReturn(BATCH_SIZE)
        .thenReturn(273);

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ false, /* enabled */ true, 7);

    task.purgeOrphanBranchRuns();

    verify(repo, times(3)).purgeOrphanBranchRunsBatch(7, BATCH_SIZE);
    verifyNoMoreInteractions(repo);
  }

  @Test
  void purgeOrphanBranchRunsStopsAfterShortFirstBatch() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);
    when(repo.purgeOrphanBranchRunsBatch(7, BATCH_SIZE)).thenReturn(42);

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ false, /* enabled */ true, 7);

    task.purgeOrphanBranchRuns();

    verify(repo).purgeOrphanBranchRunsBatch(7, BATCH_SIZE);
    verifyNoMoreInteractions(repo);
  }

  @Test
  void purgeOrphanBranchRunsStopsImmediatelyWhenBacklogIsEmpty() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);
    when(repo.purgeOrphanBranchRunsBatch(7, BATCH_SIZE)).thenReturn(0);

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ false, /* enabled */ true, 7);

    task.purgeOrphanBranchRuns();

    verify(repo).purgeOrphanBranchRunsBatch(7, BATCH_SIZE);
    verifyNoMoreInteractions(repo);
  }

  @Test
  void purgeOrphanBranchRunsPreviewsOnlyInDryRunMode() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);
    when(repo.previewOrphanBranchRunIds(14, BATCH_SIZE)).thenReturn(List.of(1L, 2L, 3L));

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ true, /* enabled */ true, 14);

    task.purgeOrphanBranchRuns();

    verify(repo).previewOrphanBranchRunIds(14, BATCH_SIZE);
    verify(repo, never()).purgeOrphanBranchRunsBatch(anyInt(), anyInt());
    verifyNoMoreInteractions(repo);
  }

  @Test
  void purgeOrphanBranchRunsShortCircuitsWhenDisabled() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ false, /* enabled */ false, 7);

    task.purgeOrphanBranchRuns();

    verifyNoMoreInteractions(repo);
  }

  @Test
  void purgeOrphanBranchRunsPropagatesGraceDaysToRepository() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);
    when(repo.purgeOrphanBranchRunsBatch(30, BATCH_SIZE)).thenReturn(0);

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ false, /* enabled */ true, 30);

    task.purgeOrphanBranchRuns();

    InOrder inOrder = Mockito.inOrder(repo);
    inOrder.verify(repo).purgeOrphanBranchRunsBatch(30, BATCH_SIZE);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void orphanBranchesDefaultsAreSafe() {
    // Defaults must keep the sweep off until an operator explicitly enables
    // it — otherwise the first scheduled tick after deploy would wipe the
    // historical backlog.
    WorkflowRunCleanupProps.OrphanBranches defaults = new WorkflowRunCleanupProps.OrphanBranches();
    assertEquals(false, defaults.isEnabled());
    assertEquals(7, defaults.getGraceDays());
    assertEquals(5000, defaults.getBatchSize());
  }

  private WorkflowRunCleanupTask createTask(
      WorkflowRunRepository repo, boolean dryRun, boolean enabled, int graceDays) {
    WorkflowRunCleanupProps props = new WorkflowRunCleanupProps();
    props.setDryRun(dryRun);
    WorkflowRunCleanupProps.OrphanBranches orphan = new WorkflowRunCleanupProps.OrphanBranches();
    orphan.setEnabled(enabled);
    orphan.setGraceDays(graceDays);
    orphan.setBatchSize(BATCH_SIZE);
    props.setOrphanBranches(orphan);
    return new WorkflowRunCleanupTask(repo, props);
  }
}
