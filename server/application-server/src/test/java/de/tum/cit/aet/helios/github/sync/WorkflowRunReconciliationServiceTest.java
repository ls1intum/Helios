package de.tum.cit.aet.helios.github.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    when(workflowRunRepository.findStaleIncompleteRuns(any(), anyList(), any(Pageable.class)))
        .thenReturn(List.of(workflowRun));
    when(gitHubService.getWorkflowRunState("owner/repo", 1001L))
        .thenReturn(
            Optional.of(new GitHubService.WorkflowRunState(
                "completed", "success", remoteUpdatedAt)));

    service.reconcileStaleWorkflowRuns();

    assertEquals(WorkflowRun.Status.COMPLETED, workflowRun.getStatus());
    assertEquals(WorkflowRun.Conclusion.SUCCESS, workflowRun.getConclusion().orElse(null));
    assertEquals(remoteUpdatedAt, workflowRun.getUpdatedAt());

    verify(workflowRunRepository).save(workflowRun);
    verifyNoMoreInteractions(workflowRunRepository);
  }

  @Test
  void reconcileStaleWorkflowRunsDoesNothingWhenDisabled() {
    ReflectionTestUtils.setField(service, "enabled", false);

    service.reconcileStaleWorkflowRuns();

    verifyNoInteractions(workflowRunRepository);
    verifyNoInteractions(gitHubService);
  }
}
