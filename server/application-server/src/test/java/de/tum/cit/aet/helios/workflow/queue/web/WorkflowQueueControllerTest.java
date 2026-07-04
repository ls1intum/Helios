package de.tum.cit.aet.helios.workflow.queue.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.workflow.queue.QueueAlertEventRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRuleRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueEtaService;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStatRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJob;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import de.tum.cit.aet.helios.workflow.queue.reconcile.WorkflowJobBackfillService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@ContextConfiguration(classes = WorkflowQueueController.class)
@WebMvcTest(value = WorkflowQueueController.class,
    properties = "helios.queue.enabled=true")
class WorkflowQueueControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean WorkflowJobRepository workflowJobRepository;
  @MockitoBean QueueWaitStatRepository statsRepository;
  @MockitoBean QueueAlertRuleRepository ruleRepository;
  @MockitoBean QueueAlertEventRepository eventRepository;
  @MockitoBean QueueEtaService etaService;
  @MockitoBean WorkflowJobBackfillService backfillService;

  private WorkflowJob job(String status, List<String> labels) {
    WorkflowJob j = new WorkflowJob();
    j.setId(1L);
    j.setRepositoryId(7L);
    j.setStatus(status);
    j.setLabels(labels);
    j.setName("build");
    j.setWorkflowRunId(99L);
    j.setHeadBranch("main");
    j.setCreatedAt(OffsetDateTime.now().minusSeconds(60));
    j.setRunnerKind(WorkflowJob.RunnerKind.SELF_HOSTED);
    return j;
  }

  @Test
  void depthAggregatesByLabelSet() throws Exception {
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(
            org.mockito.ArgumentMatchers.eq(7L), anyList()))
        .thenReturn(List.of(
            job("queued", List.of("self-hosted", "linux")),
            job("queued", List.of("self-hosted", "linux")),
            job("in_progress", List.of("self-hosted", "linux"))));

    mockMvc.perform(get("/api/queue/repos/7/depth").with(user("alice")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalQueued").value(2))
        .andExpect(jsonPath("$.totalInProgress").value(1));
  }

  @Test
  void jobsEndpointIncludesEtaResolvedFromService() throws Exception {
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(
            org.mockito.ArgumentMatchers.eq(7L), anyList(),
            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(List.of(job("queued", List.of("self-hosted", "linux"))));
    when(etaService.computeEta(any()))
        .thenReturn(new QueueEtaService.EtaResult(120L, 2, 1, null, null, false));

    mockMvc.perform(get("/api/queue/repos/7/jobs").with(user("alice")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].etaSeconds").value(120));
  }
}
