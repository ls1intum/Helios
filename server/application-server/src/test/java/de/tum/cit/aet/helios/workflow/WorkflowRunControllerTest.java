package de.tum.cit.aet.helios.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.workflow.WorkflowRun.Conclusion;
import de.tum.cit.aet.helios.workflow.WorkflowRun.Status;
import de.tum.cit.aet.helios.workflow.pagination.PaginatedWorkflowRunsResponse;
import de.tum.cit.aet.helios.workflow.pagination.WorkflowRunFilterType;
import de.tum.cit.aet.helios.workflow.pagination.WorkflowRunPageRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WorkflowRunController.class)
@WebMvcTest(WorkflowRunController.class)
class WorkflowRunControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private WorkflowRunService workflowRunService;

  private PaginatedWorkflowRunsResponse sampleResponse;

  @BeforeEach
  void setUp() {
    OffsetDateTime now = OffsetDateTime.now();
    WorkflowRunDto sampleRun = new WorkflowRunDto(
        1L,
        "Build",
        "Build workflow",
        Status.SUCCESS,
        100L,
        Conclusion.SUCCESS,
        "https://github.com/owner/repo/actions/runs/1",
        Workflow.Label.TEST,
        WorkflowRun.TestProcessingStatus.PROCESSED,
        "main",
        "abc1234",
        now.minusHours(1),
        now.minusHours(2),
        now);

    sampleResponse =
        new PaginatedWorkflowRunsResponse(List.of(sampleRun), 1, 20, 1L, 1);
  }

  @Test
  void getWorkflowRuns_returnsPaginatedResponse() throws Exception {
    when(workflowRunService.getPaginatedWorkflowRuns(any(WorkflowRunPageRequest.class)))
        .thenReturn(sampleResponse);

    mockMvc
        .perform(get("/api/workflows/runs").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runs").isArray())
        .andExpect(jsonPath("$.runs.length()").value(1))
        .andExpect(jsonPath("$.runs[0].id").value(1))
        .andExpect(jsonPath("$.runs[0].name").value("Build"))
        .andExpect(jsonPath("$.runs[0].displayTitle").value("Build workflow"))
        .andExpect(jsonPath("$.runs[0].status").value("SUCCESS"))
        .andExpect(jsonPath("$.runs[0].conclusion").value("SUCCESS"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));

    verify(workflowRunService).getPaginatedWorkflowRuns(any(WorkflowRunPageRequest.class));
  }

  @Test
  void getWorkflowRuns_usesDefaultPaginationValues() throws Exception {
    when(workflowRunService.getPaginatedWorkflowRuns(any(WorkflowRunPageRequest.class)))
        .thenReturn(sampleResponse);

    ArgumentCaptor<WorkflowRunPageRequest> requestCaptor =
        ArgumentCaptor.forClass(WorkflowRunPageRequest.class);

    mockMvc.perform(get("/api/workflows/runs").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verify(workflowRunService).getPaginatedWorkflowRuns(requestCaptor.capture());

    WorkflowRunPageRequest captured = requestCaptor.getValue();
    assertEquals(1, captured.getPage());
    assertEquals(20, captured.getSize());
  }

  @Test
  void getWorkflowRuns_acceptsCustomPaginationAndSortAndFilter() throws Exception {
    when(workflowRunService.getPaginatedWorkflowRuns(any(WorkflowRunPageRequest.class)))
        .thenReturn(sampleResponse);

    ArgumentCaptor<WorkflowRunPageRequest> requestCaptor =
        ArgumentCaptor.forClass(WorkflowRunPageRequest.class);

    mockMvc
        .perform(
            get("/api/workflows/runs")
                .param("page", "2")
                .param("size", "10")
                .param("sortField", "name")
                .param("sortDirection", "asc")
                .param("filterType", "SUCCESS")
                .param("searchTerm", "build")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verify(workflowRunService).getPaginatedWorkflowRuns(requestCaptor.capture());

    WorkflowRunPageRequest captured = requestCaptor.getValue();
    assertEquals(2, captured.getPage());
    assertEquals(10, captured.getSize());
    assertEquals("name", captured.getSortField());
    assertEquals("asc", captured.getSortDirection());
    assertEquals(WorkflowRunFilterType.SUCCESS, captured.getFilterType());
    assertEquals("build", captured.getSearchTerm());
  }

  @Test
  void getWorkflowRuns_returnsEmptyPageWhenNoRuns() throws Exception {
    PaginatedWorkflowRunsResponse empty =
        new PaginatedWorkflowRunsResponse(List.of(), 1, 20, 0L, 0);

    when(workflowRunService.getPaginatedWorkflowRuns(any(WorkflowRunPageRequest.class)))
        .thenReturn(empty);

    mockMvc
        .perform(get("/api/workflows/runs").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runs").isArray())
        .andExpect(jsonPath("$.runs.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));
  }
}
