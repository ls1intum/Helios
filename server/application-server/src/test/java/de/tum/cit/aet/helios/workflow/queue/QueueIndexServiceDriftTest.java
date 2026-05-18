package de.tum.cit.aet.helios.workflow.queue;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload.Repository;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload.WorkflowJob;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Sentinel for PR #1046 follow-up #5: {@link QueueIndexService} decrements its counter on every
 * {@code in_progress}/{@code completed} event, but NATS can redeliver these. Counter drifts.
 *
 * <p>These tests assert the <em>correct</em> behavior — once the fix lands (read prior status from
 * persistence and only transition on actual state change), remove the {@link Disabled}.
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
  @Disabled("PR #1046 follow-up #5: counter drifts on NATS redelivery of in_progress")
  void redeliveredInProgressDoesNotDoubleDecrement() {
    QueueIndexService service = new QueueIndexService();
    service.onWorkflowJobEvent(event("queued", 1L));
    service.onWorkflowJobEvent(event("in_progress", 1L));
    // Redelivery of the SAME job in the SAME state — must not drift the counter.
    service.onWorkflowJobEvent(event("in_progress", 1L));

    assertThat(service.snapshotFor(7L, List.of("self-hosted", "linux"))).isEqualTo(0);
  }

  @Test
  @Disabled("PR #1046 follow-up #5: completed events for jobs never seen also drift the counter")
  void completedForUnknownJobDoesNotPushCounterNegative() {
    QueueIndexService service = new QueueIndexService();
    // The service never saw this job queued, so completed shouldn't change anything.
    service.onWorkflowJobEvent(event("completed", 42L));

    assertThat(service.snapshotFor(7L, List.of("self-hosted", "linux"))).isEqualTo(0);
  }
}
