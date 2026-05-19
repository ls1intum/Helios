package de.tum.cit.aet.helios.workflow.queue;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload.Repository;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload.WorkflowJob;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the per-job state tracking in {@link QueueIndexService} (PR #1046 follow-up #5, fixed).
 * Counter must not drift when GitHub or NATS redelivers the same status.
 */
class QueueIndexServiceDriftTest {

  private GitHubWorkflowJobPayload event(String status, Long jobId) {
    WorkflowJob job = new WorkflowJob(
        jobId, 99L, "CI", "main", "abc", "https://x", status, null,
        OffsetDateTime.now(), null, null, "build",
        List.of("self-hosted", "linux"), null, null, null, null);
    return new GitHubWorkflowJobPayload(status, job, null, new Repository(7L, "ls1intum/Helios"));
  }

  @Test
  void redeliveredQueuedDoesNotDoubleIncrement() {
    QueueIndexService service = new QueueIndexService();
    service.onWorkflowJobEvent(event("queued", 1L));
    service.onWorkflowJobEvent(event("queued", 1L)); // duplicate
    assertThat(service.snapshotFor(7L, List.of("self-hosted", "linux"))).isEqualTo(1);
  }

  @Test
  void redeliveredInProgressDoesNotDoubleDecrement() {
    QueueIndexService service = new QueueIndexService();
    service.onWorkflowJobEvent(event("queued", 1L));
    service.onWorkflowJobEvent(event("in_progress", 1L));
    service.onWorkflowJobEvent(event("in_progress", 1L)); // redelivery
    assertThat(service.snapshotFor(7L, List.of("self-hosted", "linux"))).isEqualTo(0);
  }

  @Test
  void completedForUnknownJobDoesNotPushCounterNegative() {
    QueueIndexService service = new QueueIndexService();
    service.onWorkflowJobEvent(event("completed", 42L));
    assertThat(service.snapshotFor(7L, List.of("self-hosted", "linux"))).isEqualTo(0);
  }

  @Test
  void twoSeparateJobsTrackedIndependently() {
    QueueIndexService service = new QueueIndexService();
    service.onWorkflowJobEvent(event("queued", 1L));
    service.onWorkflowJobEvent(event("queued", 2L));
    service.onWorkflowJobEvent(event("in_progress", 1L));
    // Only job 1 moved out of queued.
    assertThat(service.snapshotFor(7L, List.of("self-hosted", "linux"))).isEqualTo(1);
  }
}
