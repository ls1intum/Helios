package de.tum.cit.aet.helios.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Conclusion;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Status;
import de.tum.cit.aet.helios.workflow.pagination.PaginatedWorkflowRunsResponse;
import de.tum.cit.aet.helios.workflow.pagination.WorkflowRunPageRequest;
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

  @BeforeEach
  public void setUp() {
    RepositoryContext.setRepositoryId("1");
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

