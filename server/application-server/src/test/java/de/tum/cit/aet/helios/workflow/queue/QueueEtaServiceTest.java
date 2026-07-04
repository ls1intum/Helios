package de.tum.cit.aet.helios.workflow.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueEtaServiceTest {

  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock RunnerRepository runnerRepository;
  @Mock QueueWaitStatRepository statsRepository;

  @InjectMocks QueueEtaService service;

  @BeforeEach
  void setupGhhCeiling() throws Exception {
    Field f = QueueEtaService.class.getDeclaredField("githubHostedCeiling");
    f.setAccessible(true);
    f.set(service, 20);
  }

  private WorkflowJob queued(Long id, List<String> labels, OffsetDateTime created) {
    WorkflowJob j = new WorkflowJob();
    j.setId(id);
    j.setRepositoryId(7L);
    j.setLabels(LabelSets.canonical(labels));
    j.setLabelSetHash(LabelSets.hash(labels));
    j.setRunnerKind(LabelSets.deriveRunnerKind(labels));
    j.setStatus("queued");
    j.setCreatedAt(created);
    return j;
  }

  private Runner runner(Long id, List<String> labels, boolean busy) {
    Runner r = new Runner();
    r.setId(id);
    r.setLabels(LabelSets.canonical(labels));
    r.setStatus(Runner.Status.ONLINE);
    r.setBusy(busy);
    return r;
  }

  @Test
  void githubHostedReturnsNullEtaWithSaturation() {
    WorkflowJob job = queued(1L,
        List.of("ubuntu-latest"), OffsetDateTime.now());
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(7L,
        List.of("queued", "in_progress")))
        .thenReturn(List.of(job));

    QueueEtaService.EtaResult r = service.computeEta(job);

    assertThat(r.etaSeconds()).isNull();
    assertThat(r.saturation()).isNotNull();
    assertThat(r.capacity()).isNull();
  }

  @Test
  void selfHostedLabelSupersetIncludedInCapacity() {
    // A job with one job ahead in the queue, on a label-set served by a runner that has a
    // SUPERSET of the needed labels.
    OffsetDateTime now = OffsetDateTime.now();
    WorkflowJob ahead = queued(1L, List.of("self-hosted", "linux"), now.minusMinutes(2));
    WorkflowJob job = queued(2L, List.of("self-hosted", "linux"), now);
    when(runnerRepository.findByStatus(Runner.Status.ONLINE))
        .thenReturn(List.of(runner(101L, List.of("self-hosted", "linux", "x64"), false)));
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(7L,
        List.of("queued"))).thenReturn(List.of(ahead, job));
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(7L,
        List.of("in_progress"))).thenReturn(List.of());

    QueueEtaService.EtaResult r = service.computeEta(job);

    assertThat(r.capacity()).isEqualTo(1);
    // queueAhead excludes the job being estimated, but includes the earlier-created job.
    assertThat(r.queueAhead()).isEqualTo(1);
    assertThat(r.etaSeconds()).isNotNull();
  }

  @Test
  void runnerWithStrictSubsetLabelsReturnsNullEta() {
    WorkflowJob job = queued(1L, List.of("self-hosted", "linux", "gpu"), OffsetDateTime.now());
    when(runnerRepository.findByStatus(Runner.Status.ONLINE))
        .thenReturn(List.of(
            // Missing `gpu` — no runner can pick this up.
            runner(101L, List.of("self-hosted", "linux"), false)));

    QueueEtaService.EtaResult r = service.computeEta(job);

    // Capacity 0 ⇒ unschedulable ⇒ ETA must be null (don't pretend it's runnable).
    assertThat(r.capacity()).isEqualTo(0);
    assertThat(r.etaSeconds()).isNull();
  }

  @Test
  void onlyQueuedJobItselfHasZeroQueueAhead() {
    // Single-runner pool with only the job we're estimating in the queue → queueAhead = 0,
    // ETA ≈ 0 (modulo currently-running jobs, of which there are none).
    OffsetDateTime now = OffsetDateTime.now();
    WorkflowJob job = queued(1L, List.of("self-hosted", "linux"), now);
    when(runnerRepository.findByStatus(Runner.Status.ONLINE))
        .thenReturn(List.of(runner(101L, List.of("self-hosted", "linux"), false)));
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(7L,
        List.of("queued"))).thenReturn(List.of(job));
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(7L,
        List.of("in_progress"))).thenReturn(List.of());

    QueueEtaService.EtaResult r = service.computeEta(job);

    assertThat(r.queueAhead()).isEqualTo(0);
    assertThat(r.etaSeconds()).isEqualTo(0L);
  }
}
