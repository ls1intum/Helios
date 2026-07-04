package de.tum.cit.aet.helios.workflow.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload.Repository;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload.WorkflowJob;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowJobPersistenceServiceTest {

  @Mock WorkflowJobRepository workflowJobRepository;
  @InjectMocks WorkflowJobPersistenceService service;

  private GitHubWorkflowJobPayload payload(WorkflowJob job, Long repoId) {
    return new GitHubWorkflowJobPayload(
        "in_progress",
        job,
        null,
        new Repository(repoId, "ls1intum/Helios"));
  }

  private WorkflowJob buildJob(String status, OffsetDateTime created, OffsetDateTime started,
      OffsetDateTime completed, List<String> labels) {
    return new WorkflowJob(
        42L, 99L, "CI", "main", "abc123", "https://x", status, null,
        created, started, completed, "build",
        labels, null, null, null, null);
  }

  @Test
  void upsertSetsDerivedFieldsForQueued() {
    when(workflowJobRepository.findById(42L)).thenReturn(Optional.empty());
    OffsetDateTime created = OffsetDateTime.parse("2026-05-18T10:00:00Z");
    service.upsert(payload(buildJob("queued", created, null, null, List.of("self-hosted", "linux")),
        7L));

    ArgumentCaptor<de.tum.cit.aet.helios.workflow.queue.WorkflowJob> captor =
        ArgumentCaptor.forClass(de.tum.cit.aet.helios.workflow.queue.WorkflowJob.class);
    verify(workflowJobRepository).save(captor.capture());
    var saved = captor.getValue();

    assertEquals(42L, saved.getId());
    assertEquals(99L, saved.getWorkflowRunId());
    assertEquals(7L, saved.getRepositoryId());
    assertEquals("queued", saved.getStatus());
    assertEquals(de.tum.cit.aet.helios.workflow.queue.WorkflowJob.RunnerKind.SELF_HOSTED,
        saved.getRunnerKind());
    assertEquals(List.of("linux", "self-hosted"), saved.getLabels());
    assertNotNull(saved.getLabelSetHash());
    assertNull(saved.getQueueWaitSeconds(), "queue wait should be null when not yet started");
    assertNull(saved.getRunDurationSeconds());
  }

  @Test
  void upsertDerivesGithubHostedRunnerKindFromUbuntu() {
    when(workflowJobRepository.findById(42L)).thenReturn(Optional.empty());
    service.upsert(payload(
        buildJob("queued", OffsetDateTime.now(), null, null, List.of("ubuntu-latest")), 7L));
    ArgumentCaptor<de.tum.cit.aet.helios.workflow.queue.WorkflowJob> captor =
        ArgumentCaptor.forClass(de.tum.cit.aet.helios.workflow.queue.WorkflowJob.class);
    verify(workflowJobRepository).save(captor.capture());
    assertEquals(de.tum.cit.aet.helios.workflow.queue.WorkflowJob.RunnerKind.GITHUB_HOSTED,
        captor.getValue().getRunnerKind());
  }

  @Test
  void upsertComputesDurationsOnCompletion() {
    when(workflowJobRepository.findById(42L)).thenReturn(Optional.empty());
    OffsetDateTime t0 = OffsetDateTime.parse("2026-05-18T10:00:00Z");
    OffsetDateTime t1 = t0.plusSeconds(30);
    OffsetDateTime t2 = t1.plusSeconds(120);
    service.upsert(payload(buildJob("completed", t0, t1, t2, List.of("self-hosted")), 7L));

    ArgumentCaptor<de.tum.cit.aet.helios.workflow.queue.WorkflowJob> captor =
        ArgumentCaptor.forClass(de.tum.cit.aet.helios.workflow.queue.WorkflowJob.class);
    verify(workflowJobRepository).save(captor.capture());
    assertEquals(30, captor.getValue().getQueueWaitSeconds());
    assertEquals(120, captor.getValue().getRunDurationSeconds());
  }

  @Test
  void upsertBailsOutWhenJobIdMissing() {
    WorkflowJob job = new WorkflowJob(null, 99L, null, null, null, null, "queued", null, null, null,
        null, null, null, null, null, null, null);
    service.upsert(payload(job, 7L));
    verify(workflowJobRepository, never()).save(any());
  }

  @Test
  void upsertBailsOutWhenRepositoryMissing() {
    WorkflowJob job = buildJob("queued", OffsetDateTime.now(), null, null, List.of("linux"));
    GitHubWorkflowJobPayload p = new GitHubWorkflowJobPayload("queued", job, null, null);
    service.upsert(p);
    verify(workflowJobRepository, never()).save(any());
  }

  @Test
  void upsertIsIdempotentOnRepeatedDelivery() {
    // First call → finds nothing, creates a new row.
    when(workflowJobRepository.findById(42L)).thenReturn(Optional.empty());
    OffsetDateTime t0 = OffsetDateTime.parse("2026-05-18T10:00:00Z");
    var p = payload(buildJob("queued", t0, null, null, List.of("self-hosted")), 7L);
    service.upsert(p);

    // Now simulate the same payload arriving a second time — repository should be called via
    // findById and update the existing row, not insert a new one.
    de.tum.cit.aet.helios.workflow.queue.WorkflowJob existing =
        new de.tum.cit.aet.helios.workflow.queue.WorkflowJob();
    existing.setId(42L);
    when(workflowJobRepository.findById(42L)).thenReturn(Optional.of(existing));
    service.upsert(p);

    ArgumentCaptor<de.tum.cit.aet.helios.workflow.queue.WorkflowJob> captor =
        ArgumentCaptor.forClass(de.tum.cit.aet.helios.workflow.queue.WorkflowJob.class);
    verify(workflowJobRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    // Both saves write the same primary key — second call must update, not insert.
    assertEquals(42L, captor.getAllValues().get(0).getId());
    assertEquals(42L, captor.getAllValues().get(1).getId());
  }

  @Test
  void upsertPreservesStatusCase() {
    // Webhook always sends lowercase. We must not transform it because the partial index on
    // workflow_job WHERE status='queued' is case-sensitive in Postgres.
    when(workflowJobRepository.findById(42L)).thenReturn(Optional.empty());
    OffsetDateTime t = OffsetDateTime.parse("2026-05-18T10:00:00Z");
    service.upsert(payload(buildJob("queued", t, null, null, List.of("self-hosted")), 7L));

    ArgumentCaptor<de.tum.cit.aet.helios.workflow.queue.WorkflowJob> captor =
        ArgumentCaptor.forClass(de.tum.cit.aet.helios.workflow.queue.WorkflowJob.class);
    verify(workflowJobRepository).save(captor.capture());
    assertEquals("queued", captor.getValue().getStatus());
  }

  @Test
  void upsertMergesIntoExistingRow() {
    de.tum.cit.aet.helios.workflow.queue.WorkflowJob existing =
        new de.tum.cit.aet.helios.workflow.queue.WorkflowJob();
    existing.setId(42L);
    existing.setName("old");
    when(workflowJobRepository.findById(42L)).thenReturn(Optional.of(existing));
    OffsetDateTime t = OffsetDateTime.parse("2026-05-18T10:00:00Z");
    service.upsert(payload(buildJob("queued", t, null, null, List.of("self-hosted")), 7L));

    ArgumentCaptor<de.tum.cit.aet.helios.workflow.queue.WorkflowJob> captor =
        ArgumentCaptor.forClass(de.tum.cit.aet.helios.workflow.queue.WorkflowJob.class);
    verify(workflowJobRepository).save(captor.capture());
    assertEquals("build", captor.getValue().getName());
    assertEquals(42L, captor.getValue().getId());
  }
}
