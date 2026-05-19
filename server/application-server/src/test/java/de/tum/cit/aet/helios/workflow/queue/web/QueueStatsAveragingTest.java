package de.tum.cit.aet.helios.workflow.queue.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.workflow.queue.QueueAlertEventRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRuleRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueEtaService;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStat;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStatRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import de.tum.cit.aet.helios.workflow.queue.reconcile.WorkflowJobBackfillService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sentinel for PR #1046 follow-up #7: {@link WorkflowQueueController#stats(...)} averages
 * per-bucket percentiles, which is statistically wrong. A bucket with 1 sample at p95=600 and a
 * bucket with 1000 samples at p95=100 should yield p95 ≈ 100, not 350.
 */
@AutoConfigureMockMvc
@ContextConfiguration(classes = WorkflowQueueController.class)
@WebMvcTest(value = WorkflowQueueController.class,
    properties = "helios.queue.enabled=true")
class QueueStatsAveragingTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean WorkflowJobRepository workflowJobRepository;
  @MockitoBean QueueWaitStatRepository statsRepository;
  @MockitoBean QueueAlertRuleRepository ruleRepository;
  @MockitoBean QueueAlertEventRepository eventRepository;
  @MockitoBean QueueEtaService etaService;
  @MockitoBean WorkflowJobBackfillService backfillService;

  private QueueWaitStat bucket(int samples, int queueP95, int runP50) {
    QueueWaitStat s = new QueueWaitStat();
    s.setRepositoryId(7L);
    s.setBucketStart(OffsetDateTime.now().minusHours(1));
    s.setSamples(samples);
    s.setQueueP50(queueP95);
    s.setQueueP90(queueP95);
    s.setQueueP95(queueP95);
    s.setRunP50(runP50);
    s.setRunP90(runP50);
    s.setRunP95(runP50);
    return s;
  }

  @Test
  void weightsP95BySamplesNotByBucketCount() throws Exception {
    // 1 outlier sample at p95=600s, 1000 normal samples at p95=100s.
    // Correct sample-weighted p95 ≈ 100; the current (wrong) implementation returns ~350.
    when(statsRepository.findForWindow(any(), any(), any(), any(), any()))
        .thenReturn(List.of(bucket(1, 600, 50), bucket(1000, 100, 60)));

    mockMvc.perform(get("/api/queue/repos/7/stats?window=7d").with(user("alice")))
        .andExpect(status().isOk())
        // Tolerance: any reasonable sample-weighted aggregate is well below 350.
        .andExpect(jsonPath("$.queueP95").value(org.hamcrest.Matchers.lessThan(200)));
  }
}
