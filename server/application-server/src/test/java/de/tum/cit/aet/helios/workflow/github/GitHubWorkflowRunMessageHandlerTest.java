package de.tum.cit.aet.helios.workflow.github;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class GitHubWorkflowRunMessageHandlerTest {

  @Mock private GitHubRepositorySyncService repositorySyncService;
  @Mock private GitHubWorkflowRunSyncService workflowSyncService;
  @Mock private GitHubService gitHubService;
  @Mock private TaskScheduler taskScheduler;

  private GitHubWorkflowRunMessageHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new GitHubWorkflowRunMessageHandler(
            repositorySyncService,
            workflowSyncService,
            gitHubService,
            taskScheduler);
  }

  @Test
  void handleInstalledRepositoryEventProcessesCompletedRunsWithoutAutomaticLogCaching()
      throws Exception {
    GHEventPayload.WorkflowRun eventPayload = mock(GHEventPayload.WorkflowRun.class);
    GHRepository repository = mock(GHRepository.class);
    GHWorkflowRun githubRun = mock(GHWorkflowRun.class);

    when(eventPayload.getAction()).thenReturn("completed");
    when(eventPayload.getRepository()).thenReturn(repository);
    when(eventPayload.getWorkflowRun()).thenReturn(githubRun);
    when(repository.getFullName()).thenReturn("owner/repo");
    when(githubRun.getEvent()).thenReturn(GHEvent.PUSH);
    when(githubRun.getPullRequests()).thenReturn(Collections.emptyList());

    handler.handleInstalledRepositoryEvent(eventPayload);

    verify(repositorySyncService).processRepository(repository);
    verify(workflowSyncService).processRun(githubRun);
  }

  @Test
  void handleInstalledRepositoryEventProcessesIncompleteRunsWithoutAutomaticLogCaching()
      throws Exception {
    GHEventPayload.WorkflowRun eventPayload = mock(GHEventPayload.WorkflowRun.class);
    GHRepository repository = mock(GHRepository.class);
    GHWorkflowRun githubRun = mock(GHWorkflowRun.class);

    when(eventPayload.getAction()).thenReturn("in_progress");
    when(eventPayload.getRepository()).thenReturn(repository);
    when(eventPayload.getWorkflowRun()).thenReturn(githubRun);
    when(repository.getFullName()).thenReturn("owner/repo");
    when(githubRun.getEvent()).thenReturn(GHEvent.PUSH);
    when(githubRun.getPullRequests()).thenReturn(Collections.emptyList());

    handler.handleInstalledRepositoryEvent(eventPayload);

    verify(repositorySyncService).processRepository(repository);
    verify(workflowSyncService).processRun(githubRun);
  }
}
