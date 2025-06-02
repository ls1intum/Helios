package de.tum.cit.aet.helios.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = DeploymentController.class)
@WebMvcTest(DeploymentController.class)
public class DeploymentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeploymentService deploymentService;

    @Autowired
    private ObjectMapper objectMapper;

    private DeploymentDto sampleDeployment;
    private List<DeploymentDto> deployments;
    private ActivityHistoryDto sampleActivity;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();

        sampleDeployment = new DeploymentDto(
            1L,
            null,
            "http://test.url",
            Deployment.State.PENDING,
            "http://statuses.url",
            "abc123",
            "main",
            "deploy",
            null,
            null,
            now,
            now
        );

        deployments = List.of(sampleDeployment);

        sampleActivity = new ActivityHistoryDto(
            "DEPLOYMENT",
            1L,
            null,
            Deployment.State.PENDING,
            "abc123",
            "main",
            null,
            null,
            now,
            now,
            now
        );
    }

    @Test
    void testGetAllDeployments() throws Exception {
        when(deploymentService.getAllDeployments()).thenReturn(deployments);
        String response = mockMvc.perform(get("/api/deployments")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertEquals(objectMapper.writeValueAsString(deployments), response);
    }
    @Test
    void testGetDeploymentById() throws Exception {
        when(deploymentService.getDeploymentById(1L))
            .thenReturn(Optional.of(sampleDeployment));

        String response = mockMvc.perform(get("/api/deployments/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertEquals(objectMapper.writeValueAsString(sampleDeployment), response);
    }

    @Test
    void testDeployToEnvironment() throws Exception {
        DeployRequest deployRequest = new DeployRequest(1L, "main", "sha123");
        
        mockMvc.perform(post("/api/deployments/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deployRequest)))
            .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void testCancelDeployment() throws Exception {
        CancelDeploymentRequest cancelRequest = new CancelDeploymentRequest(123L);
        when(deploymentService.cancelDeployment(cancelRequest))
            .thenReturn("Deployment cancelled successfully");

        String response = mockMvc.perform(post("/api/deployments/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelRequest)))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertEquals("Deployment cancelled successfully", response);
    }

    @Test
    void testGetActivityHistory() throws Exception {
        List<ActivityHistoryDto> history = List.of(sampleActivity);
        when(deploymentService.getActivityHistoryByEnvironmentId(1L))
            .thenReturn(history);

        String response = mockMvc.perform(get("/api/deployments/environment/{environmentId}/activity-history", 1L)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertEquals(objectMapper.writeValueAsString(history), response);
    }
    @Test
    void testGetWorkflowJobStatus() throws Exception {
        WorkflowJobsResponse response = new WorkflowJobsResponse();
        response.setTotalCount(0);
        response.setJobs(List.of());
        
        when(deploymentService.getWorkflowJobStatus(1L))
            .thenReturn(response);

        String actualResponse = mockMvc.perform(get("/api/deployments/workflowJobStatus/{runId}", 1L)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertEquals(objectMapper.writeValueAsString(response), actualResponse);
    }

    @Test
    void testGetNonExistingDeployment() throws Exception {
        when(deploymentService.getDeploymentById(999L))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/deployments/{id}", 999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isNotFound());
    }
}
