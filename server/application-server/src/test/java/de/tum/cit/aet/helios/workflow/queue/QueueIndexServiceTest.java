package de.tum.cit.aet.helios.workflow.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload.Repository;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload.WorkflowJob;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueueIndexServiceTest {

  private GitHubWorkflowJobPayload event(String status, Long jobId, List<String> labels) {
    WorkflowJob job = new WorkflowJob(
        jobId, 99L, "CI", "main", "abc", "https://x", status, null,
        OffsetDateTime.now(), null, null, "build",
        labels, null, null, null, null);
    return new GitHubWorkflowJobPayload(status, job, null, new Repository(7L, "ls1intum/Helios"));
  }

  @Test
  void queuedIncrementsThenInProgressDecrements() {
    QueueIndexService service = new QueueIndexService();
    service.onWorkflowJobEvent(event("queued", 1L, List.of("self-hosted", "linux")));
    assertEquals(1, service.snapshotFor(7L, List.of("self-hosted", "linux")));
    service.onWorkflowJobEvent(event("in_progress", 1L, List.of("self-hosted", "linux")));
    assertEquals(0, service.snapshotFor(7L, List.of("self-hosted", "linux")));
  }

  @Test
  void differentLabelSetsTrackedSeparately() {
    QueueIndexService service = new QueueIndexService();
    service.onWorkflowJobEvent(event("queued", 1L, List.of("self-hosted", "linux")));
    service.onWorkflowJobEvent(event("queued", 2L, List.of("ubuntu-latest")));
    assertEquals(1, service.snapshotFor(7L, List.of("self-hosted", "linux")));
    assertEquals(1, service.snapshotFor(7L, List.of("ubuntu-latest")));
    assertNotEquals(
        service.snapshotFor(7L, List.of("self-hosted", "linux")),
        service.snapshotFor(99L, List.of("self-hosted", "linux")));
  }

  @Test
  void counterDoesNotGoNegative() {
    QueueIndexService service = new QueueIndexService();
    service.onWorkflowJobEvent(event("in_progress", 1L, List.of("linux")));
    service.onWorkflowJobEvent(event("completed", 2L, List.of("linux")));
    assertEquals(0, service.snapshotFor(7L, List.of("linux")));
  }

  @Test
  void unknownStatusIsNoop() {
    QueueIndexService service = new QueueIndexService();
    // "other" is mapped to JobState.OTHER which neither increments nor decrements the counter.
    service.onWorkflowJobEvent(event("scheduled", 1L, List.of("linux")));
    assertEquals(0, service.snapshotFor(7L, List.of("linux")));
  }
}
