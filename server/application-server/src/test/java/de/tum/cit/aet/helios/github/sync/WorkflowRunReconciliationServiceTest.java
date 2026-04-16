package de.tum.cit.aet.helios.github.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WorkflowRunReconciliationServiceTest {

  @Mock
  private WorkflowRunRepository workflowRunRepository;

  @Mock
  private GitHubService gitHubService;

  @InjectMocks
  private WorkflowRunReconciliationService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "enabled", true);
  }

  @Test
  void reconcileStaleWorkflowRunsUpdatesStatusAndConclusion() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(42L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun workflowRun = new WorkflowRun();
    workflowRun.setId(1001L);
    workflowRun.setRepository(repository);
    workflowRun.setStatus(WorkflowRun.Status.IN_PROGRESS);
    workflowRun.setUpdatedAt(OffsetDateTime.now().minusHours(2));

    OffsetDateTime remoteUpdatedAt = OffsetDateTime.now();

    when(
        workflowRunRepository.findStaleIncompleteRunsFirstPage(
            any(), anyList(), any(Pageable.class)))
        .thenReturn(List.of(workflowRun));
    when(
        workflowRunRepository.findStaleIncompleteRunsAfterCursor(
            any(), anyList(), any(), anyLong(), any(Pageable.class)))
        .thenReturn(List.of());
    when(gitHubService.getWorkflowRunState("owner/repo", 1001L))
        .thenReturn(
            Optional.of(new GitHubService.WorkflowRunState(
                "completed", "success", remoteUpdatedAt)));

    service.reconcileStaleWorkflowRuns();

    assertEquals(WorkflowRun.Status.COMPLETED, workflowRun.getStatus());
    assertEquals(WorkflowRun.Conclusion.SUCCESS, workflowRun.getConclusion().orElse(null));
    assertEquals(remoteUpdatedAt, workflowRun.getUpdatedAt());

    verify(workflowRunRepository).save(workflowRun);
    verify(workflowRunRepository)
        .findStaleIncompleteRunsFirstPage(any(), anyList(), any(Pageable.class));
    verify(workflowRunRepository)
        .findStaleIncompleteRunsAfterCursor(
            any(), anyList(), any(), anyLong(), any(Pageable.class));
  }

  @Test
  void reconcileStaleWorkflowRunsContinuesToLaterPagesWhenFirstPageHasUnresolvableRows()
      throws Exception {
    WorkflowRun unresolvableWorkflowRun = new WorkflowRun();
    unresolvableWorkflowRun.setId(2001L);
    unresolvableWorkflowRun.setRepository(null);
    unresolvableWorkflowRun.setStatus(WorkflowRun.Status.IN_PROGRESS);

    GitRepository repository = new GitRepository();
    repository.setRepositoryId(43L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun reconcilableWorkflowRun = new WorkflowRun();
    reconcilableWorkflowRun.setId(2002L);
    reconcilableWorkflowRun.setRepository(repository);
    reconcilableWorkflowRun.setStatus(WorkflowRun.Status.IN_PROGRESS);
    reconcilableWorkflowRun.setUpdatedAt(OffsetDateTime.now().minusHours(2));

    OffsetDateTime remoteUpdatedAt = OffsetDateTime.now();

    when(
            workflowRunRepository.findStaleIncompleteRunsFirstPage(
                any(), anyList(), any(Pageable.class)))
        .thenReturn(List.of(unresolvableWorkflowRun));
    when(
            workflowRunRepository.findStaleIncompleteRunsAfterCursor(
                any(), anyList(), any(), anyLong(), any(Pageable.class)))
        .thenReturn(List.of(reconcilableWorkflowRun), List.of());
    when(gitHubService.getWorkflowRunState("owner/repo", 2002L))
        .thenReturn(
            Optional.of(new GitHubService.WorkflowRunState(
                "completed", "success", remoteUpdatedAt)));

    service.reconcileStaleWorkflowRuns();

    assertEquals(WorkflowRun.Status.COMPLETED, reconcilableWorkflowRun.getStatus());
    assertEquals(
        WorkflowRun.Conclusion.SUCCESS, reconcilableWorkflowRun.getConclusion().orElse(null));
    assertEquals(remoteUpdatedAt, reconcilableWorkflowRun.getUpdatedAt());
    verify(workflowRunRepository).save(reconcilableWorkflowRun);
    verify(workflowRunRepository)
        .findStaleIncompleteRunsFirstPage(any(), anyList(), any(Pageable.class));
    verify(workflowRunRepository, times(2))
        .findStaleIncompleteRunsAfterCursor(
            any(), anyList(), any(), anyLong(), any(Pageable.class));
  }

  @Test
  void reconcileStaleWorkflowRunsUsesCursorPaginationNotOffsetPagination() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(44L);
    repository.setNameWithOwner("owner/repo");

    OffsetDateTime firstTs = OffsetDateTime.now().minusHours(4);
    WorkflowRun first = new WorkflowRun();
    first.setId(3001L);
    first.setRepository(repository);
    first.setStatus(WorkflowRun.Status.IN_PROGRESS);
    first.setUpdatedAt(firstTs);

    OffsetDateTime secondTs = OffsetDateTime.now().minusHours(3);
    WorkflowRun second = new WorkflowRun();
    second.setId(3002L);
    second.setRepository(repository);
    second.setStatus(WorkflowRun.Status.IN_PROGRESS);
    second.setUpdatedAt(secondTs);

    when(
            workflowRunRepository.findStaleIncompleteRunsFirstPage(
                any(), anyList(), any(Pageable.class)))
        .thenReturn(List.of(first));
    when(
            workflowRunRepository.findStaleIncompleteRunsAfterCursor(
                any(), anyList(), any(), anyLong(), any(Pageable.class)))
        .thenReturn(List.of(second), List.of());
    when(gitHubService.getWorkflowRunState("owner/repo", 3001L))
        .thenReturn(
            Optional.of(new GitHubService.WorkflowRunState(
                "completed", "success", firstTs.plusMinutes(1))));
    when(gitHubService.getWorkflowRunState("owner/repo", 3002L))
        .thenReturn(
            Optional.of(new GitHubService.WorkflowRunState(
                "completed", "success", secondTs.plusMinutes(1))));

    service.reconcileStaleWorkflowRuns();

    ArgumentCaptor<OffsetDateTime> cursorTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    ArgumentCaptor<Long> cursorIdCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Pageable> firstPageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    ArgumentCaptor<Pageable> cursorPageableCaptor = ArgumentCaptor.forClass(Pageable.class);

    verify(workflowRunRepository)
        .findStaleIncompleteRunsFirstPage(
            any(), anyList(), firstPageableCaptor.capture());
    verify(workflowRunRepository, times(2))
        .findStaleIncompleteRunsAfterCursor(
            any(),
            anyList(),
            cursorTimeCaptor.capture(),
            cursorIdCaptor.capture(),
            cursorPageableCaptor.capture());

    List<OffsetDateTime> cursorTimes = cursorTimeCaptor.getAllValues();
    assertEquals(firstTs.plusMinutes(1), cursorTimes.get(0));
    assertEquals(secondTs.plusMinutes(1), cursorTimes.get(1));

    List<Long> cursorIds = cursorIdCaptor.getAllValues();
    assertEquals(3001L, cursorIds.get(0));
    assertEquals(3002L, cursorIds.get(1));

    assertEquals(0, firstPageableCaptor.getValue().getPageNumber());
    List<Pageable> cursorPageables = cursorPageableCaptor.getAllValues();
    assertEquals(0, cursorPageables.get(0).getPageNumber());
    assertEquals(0, cursorPageables.get(1).getPageNumber());
  }

  @Test
  void reconcileStaleWorkflowRunsDoesNothingWhenDisabled() {
    ReflectionTestUtils.setField(service, "enabled", false);

    service.reconcileStaleWorkflowRuns();

    verifyNoInteractions(workflowRunRepository);
    verifyNoInteractions(gitHubService);
  }
}
