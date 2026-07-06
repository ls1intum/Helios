package de.tum.cit.aet.helios.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestSuite;
import de.tum.cit.aet.helios.tests.TestSuiteRepository;
import de.tum.cit.aet.helios.tests.type.TestType;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Conclusion;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Status;
import de.tum.cit.aet.helios.workflow.pagination.PaginatedWorkflowRunsResponse;
import de.tum.cit.aet.helios.workflow.pagination.WorkflowRunPageRequest;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
public class WorkflowRunServiceTest {

  @InjectMocks private WorkflowRunService workflowRunService;

  @Mock private WorkflowRunRepository workflowRunRepository;
  @Mock private PullRequestRepository pullRequestRepository;
  @Mock private BranchRepository branchRepository;
  @Mock private GitHubService gitHubService;
  @Mock private GitRepoRepository gitRepoRepository;
  @Mock private TestSuiteRepository testSuiteRepository;

  @BeforeEach
  public void setUp() {
    RepositoryContext.setRepositoryId("1");
  }

  @AfterEach
  public void tearDown() {
    RepositoryContext.clear();
  }

  @Test
  public void getPaginatedWorkflowRuns_returnsMappedRunsAndPageMetadata() {
    WorkflowRun run1 = createWorkflowRun(
        1L, "Build", "Build workflow", Status.SUCCESS, Conclusion.SUCCESS);
    WorkflowRun run2 = createWorkflowRun(
        2L, "Tests", "Test workflow", Status.FAILURE, Conclusion.FAILURE);

    Page<WorkflowRun> page =
        new PageImpl<>(List.of(run1, run2), Pageable.ofSize(20), 2);

    when(workflowRunRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    WorkflowRunPageRequest request = WorkflowRunPageRequest.builder()
        .page(1)
        .size(20)
        .build();

    PaginatedWorkflowRunsResponse response = workflowRunService.getPaginatedWorkflowRuns(request);

    assertNotNull(response);
    assertEquals(2, response.runs().size());
    assertEquals(1L, response.runs().get(0).id());
    assertEquals("Build", response.runs().get(0).name());
    assertEquals(1, response.page());
    assertEquals(20, response.size());
    assertEquals(2, response.totalElements());
    assertEquals(1, response.totalPages());
  }

  @Test
  public void getPaginatedWorkflowRuns_usesDefaultSortingByRunStartedAtDescThenCreatedAtDesc() {
    WorkflowRun run = createWorkflowRun(
        1L, "Build", "Build workflow", Status.IN_PROGRESS, null);
    Page<WorkflowRun> page = new PageImpl<>(List.of(run), Pageable.ofSize(20), 1);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

    when(workflowRunRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    WorkflowRunPageRequest request = WorkflowRunPageRequest.builder()
        .page(1)
        .size(20)
        .sortField(null)
        .sortDirection(null)
        .build();

    workflowRunService.getPaginatedWorkflowRuns(request);

    verify(workflowRunRepository).findAll(any(Specification.class), pageableCaptor.capture());

    Pageable pageable = pageableCaptor.getValue();
    assertEquals(0, pageable.getPageNumber());
    assertEquals(20, pageable.getPageSize());

    Sort sort = pageable.getSort();
    Sort.Order runStartedAtOrder = sort.getOrderFor("runStartedAt");
    Sort.Order createdAtOrder = sort.getOrderFor("createdAt");

    assertNotNull(runStartedAtOrder);
    assertEquals(Sort.Direction.DESC, runStartedAtOrder.getDirection());

    assertNotNull(createdAtOrder);
    assertEquals(Sort.Direction.DESC, createdAtOrder.getDirection());
  }

  @Test
  public void getPaginatedWorkflowRuns_usesCustomSortingWhenRequested() {
    WorkflowRun run = createWorkflowRun(
        1L, "Build", "Build workflow", Status.SUCCESS, Conclusion.SUCCESS);
    Page<WorkflowRun> page = new PageImpl<>(List.of(run), Pageable.ofSize(10), 1);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

    when(workflowRunRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    WorkflowRunPageRequest request =
        WorkflowRunPageRequest.builder()
            .page(2)
            .size(10)
            .sortField("name")
            .sortDirection("asc")
            .build();

    workflowRunService.getPaginatedWorkflowRuns(request);

    verify(workflowRunRepository).findAll(any(Specification.class), pageableCaptor.capture());

    Pageable pageable = pageableCaptor.getValue();
    assertNotNull(pageable);
    assertEquals(1, pageable.getPageNumber());
    assertEquals(10, pageable.getPageSize());

    Sort.Order nameOrder = pageable.getSort().getOrderFor("name");
    assertNotNull(nameOrder);
    assertEquals(Sort.Direction.ASC, nameOrder.getDirection());

    // secondary default sort is still appended
    assertNotNull(pageable.getSort().getOrderFor("runStartedAt"));
    assertNotNull(pageable.getSort().getOrderFor("createdAt"));
  }

  @Test
  public void getWorkflowRunById_usesRepositoryScopedLookup() {
    WorkflowRun run = createWorkflowRun(
        100L, "Build", "Build workflow", Status.SUCCESS, Conclusion.SUCCESS);
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(eq(100L), eq(1L)))
        .thenReturn(Optional.of(run));

    WorkflowRunDto result = workflowRunService.getWorkflowRunById(100L);

    assertEquals(100L, result.id());
    verify(workflowRunRepository).findByIdAndRepositoryRepositoryId(100L, 1L);
  }

  @Test
  public void getWorkflowRunById_whenRunNotInRepository_throwsNotFound() {
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(eq(100L), eq(1L)))
        .thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> workflowRunService.getWorkflowRunById(100L));
    verify(workflowRunRepository).findByIdAndRepositoryRepositoryId(100L, 1L);
  }

  @Test
  public void cancelWorkflowRun_success_callsGithub() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun run = new WorkflowRun();

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(1L, 1L))
        .thenReturn(Optional.of(run));

    workflowRunService.cancelWorkflowRun(1L);

    verify(gitHubService).cancelWorkflowRun("owner/repo", 1L);
  }

  @Test
  public void cancelWorkflowRun_whenRunNotInRepository_doesNotCallGithub() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(eq(200L), eq(1L)))
        .thenReturn(Optional.empty());
    lenient().when(workflowRunRepository.findById(200L))
        .thenReturn(Optional.of(new WorkflowRun()));

    assertThrows(EntityNotFoundException.class, () -> workflowRunService.cancelWorkflowRun(200L));

    verify(gitHubService, never()).cancelWorkflowRun(any(), anyLong());
  }

  @Test
  public void reRunWorkflowRun_success_callsGithub() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun run = new WorkflowRun();
    run.setId(201L);
    Workflow workflow = new Workflow();
    workflow.setTestTypes(new HashSet<>(List.of(new TestType())));
    run.setWorkflow(workflow);

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(201L, 1L))
        .thenReturn(Optional.of(run));
    when(testSuiteRepository.findByWorkflowRunId(201L)).thenReturn(List.of());

    workflowRunService.reRunWorkflow(201L);

    verify(gitHubService).reRunWorkflow("owner/repo", 201L);
    verify(workflowRunRepository).save(run);
  }

  @Test
  public void reRunWorkflow_whenTestWorkflowAndExistingSuites_resetsStateAfterGithubCall()
      throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun run = new WorkflowRun();
    run.setId(205L);
    run.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSED);
    Workflow workflow = new Workflow();
    workflow.setTestTypes(new HashSet<>(List.of(new TestType())));
    run.setWorkflow(workflow);

    TestSuite existingSuite = new TestSuite();

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(205L, 1L))
        .thenReturn(Optional.of(run));
    when(testSuiteRepository.findByWorkflowRunId(205L)).thenReturn(List.of(existingSuite));

    workflowRunService.reRunWorkflow(205L);

    InOrder inOrder = inOrder(gitHubService, testSuiteRepository);
    inOrder.verify(gitHubService).reRunWorkflow("owner/repo", 205L);
    inOrder.verify(testSuiteRepository).findByWorkflowRunId(205L);
    verify(testSuiteRepository).deleteAll(List.of(existingSuite));
    verify(workflowRunRepository).save(run);
    assertNull(run.getTestProcessingStatus());
  }

  @Test
  public void reRunWorkflow_whenRunNotInRepository_doesNotCallGithub() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(eq(201L), eq(1L)))
        .thenReturn(Optional.empty());
    lenient().when(workflowRunRepository.findById(200L))
        .thenReturn(Optional.of(new WorkflowRun()));

    assertThrows(EntityNotFoundException.class, () -> workflowRunService.reRunWorkflow(201L));

    verify(gitHubService, never()).reRunWorkflow(any(), anyLong());
  }

  @Test
  public void reRunFailedJobs_success_callsGithub() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun run = new WorkflowRun();
    run.setId(202L);
    Workflow workflow = new Workflow();
    workflow.setTestTypes(new HashSet<>(List.of(new TestType())));
    run.setWorkflow(workflow);

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(202L, 1L))
        .thenReturn(Optional.of(run));
    when(testSuiteRepository.findByWorkflowRunId(202L)).thenReturn(List.of());

    workflowRunService.reRunFailedJobs(202L);

    verify(gitHubService).reRunFailedJobs("owner/repo", 202L);
    verify(workflowRunRepository).save(run);
  }

  @Test
  public void reRunWorkflow_whenWorkflowHasNoTestTypes_doesNotResetTestState() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun run = new WorkflowRun();
    Workflow workflow = new Workflow();
    workflow.setTestTypes(new HashSet<>());
    run.setWorkflow(workflow);

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(203L, 1L))
        .thenReturn(Optional.of(run));

    workflowRunService.reRunWorkflow(203L);

    verify(gitHubService).reRunWorkflow("owner/repo", 203L);
    verify(testSuiteRepository, never()).findByWorkflowRunId(anyLong());
  }

  @Test
  public void reRunWorkflow_whenGithubRerunFails_doesNotResetTestState() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun run = new WorkflowRun();
    Workflow workflow = new Workflow();
    workflow.setTestTypes(new HashSet<>(List.of(new TestType())));
    run.setWorkflow(workflow);

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(204L, 1L))
        .thenReturn(Optional.of(run));
    doThrow(new IOException("Dummy error")).when(gitHubService).reRunWorkflow("owner/repo", 204L);

    assertThrows(RuntimeException.class, () -> workflowRunService.reRunWorkflow(204L));

    verify(testSuiteRepository, never()).findByWorkflowRunId(anyLong());
    verify(workflowRunRepository, never()).save(run);
  }

  @Test
  public void reRunFailedJobs_whenGithubRerunFails_doesNotResetTestState() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun run = new WorkflowRun();
    Workflow workflow = new Workflow();
    workflow.setTestTypes(new HashSet<>(List.of(new TestType())));
    run.setWorkflow(workflow);

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(206L, 1L))
        .thenReturn(Optional.of(run));
    doThrow(new IOException("Dummy error")).when(gitHubService).reRunFailedJobs("owner/repo", 206L);

    assertThrows(RuntimeException.class, () -> workflowRunService.reRunFailedJobs(206L));

    verify(testSuiteRepository, never()).findByWorkflowRunId(anyLong());
    verify(workflowRunRepository, never()).save(run);
  }

  @Test
  public void reRunFailedJobs_whenRunNotInRepository_doesNotCallGithub() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("owner/repo");

    when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
    when(workflowRunRepository.findByIdAndRepositoryRepositoryId(eq(202L), eq(1L)))
        .thenReturn(Optional.empty());
    lenient().when(workflowRunRepository.findById(200L))
        .thenReturn(Optional.of(new WorkflowRun()));

    assertThrows(EntityNotFoundException.class, () -> workflowRunService.reRunFailedJobs(202L));

    verify(gitHubService, never()).reRunFailedJobs(any(), anyLong());
  }

  private WorkflowRun createWorkflowRun(
      Long id, String name, String displayTitle, Status status, Conclusion conclusion) {
    WorkflowRun run = new WorkflowRun();
    run.setId(id);
    run.setName(name);
    run.setDisplayTitle(displayTitle);
    run.setStatus(status);
    run.setConclusion(Optional.ofNullable(conclusion));
    run.setHtmlUrl("https://example.com/workflows/" + id);
    run.setHeadBranch("main");
    run.setHeadSha("abcdef" + id);
    run.setRunStartedAt(OffsetDateTime.now().minusHours(1));
    run.setCreatedAt(OffsetDateTime.now().minusHours(2));
    run.setUpdatedAt(OffsetDateTime.now());

    Workflow workflow = new Workflow();
    workflow.setId(100L);
    workflow.setLabel(Workflow.Label.TEST);
    run.setWorkflow(workflow);

    return run;
  }
}

