package de.tum.cit.aet.helios.workflow.cleanup;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkflowRunCleanupTaskTest {

  @Test
  void purgeOrphanBranchRunsDeletesInDeleteMode() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);
    when(repo.purgeOrphanBranchRuns(7)).thenReturn(42);

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ false, /* enabled */ true, 7);

    task.purgeOrphanBranchRuns();

    verify(repo).purgeOrphanBranchRuns(7);
    verifyNoMoreInteractions(repo);
  }

  @Test
  void purgeOrphanBranchRunsPreviewsOnlyInDryRunMode() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);
    when(repo.previewOrphanBranchRunIds(14)).thenReturn(List.of(1L, 2L, 3L));

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ true, /* enabled */ true, 14);

    task.purgeOrphanBranchRuns();

    verify(repo).previewOrphanBranchRunIds(14);
    verify(repo, never()).purgeOrphanBranchRuns(anyInt());
    verifyNoMoreInteractions(repo);
  }

  @Test
  void purgeOrphanBranchRunsShortCircuitsWhenDisabled() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ false, /* enabled */ false, 7);

    task.purgeOrphanBranchRuns();

    verify(repo, never()).purgeOrphanBranchRuns(anyInt());
    verify(repo, never()).previewOrphanBranchRunIds(anyInt());
    verifyNoMoreInteractions(repo);
  }

  @Test
  void purgeOrphanBranchRunsPropagatesGraceDaysToRepository() {
    WorkflowRunRepository repo = Mockito.mock(WorkflowRunRepository.class);
    when(repo.purgeOrphanBranchRuns(30)).thenReturn(0);

    WorkflowRunCleanupTask task = createTask(repo, /* dryRun */ false, /* enabled */ true, 30);

    task.purgeOrphanBranchRuns();

    verify(repo).purgeOrphanBranchRuns(30);
  }

  private WorkflowRunCleanupTask createTask(
      WorkflowRunRepository repo, boolean dryRun, boolean enabled, int graceDays) {
    WorkflowRunCleanupProps props = new WorkflowRunCleanupProps();
    props.setDryRun(dryRun);
    WorkflowRunCleanupProps.OrphanBranches orphan = new WorkflowRunCleanupProps.OrphanBranches();
    orphan.setEnabled(enabled);
    orphan.setGraceDays(graceDays);
    props.setOrphanBranches(orphan);
    return new WorkflowRunCleanupTask(repo, props);
  }
}
