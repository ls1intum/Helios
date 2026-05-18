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
    WorkflowJob job = queued(1L, List.of("self-hosted", "linux"), OffsetDateTime.now());
    when(runnerRepository.findByStatus(Runner.Status.ONLINE))
        .thenReturn(List.of(
            // Runner has a SUPERSET of needed labels — must be counted.
            runner(101L, List.of("self-hosted", "linux", "x64"), false)));
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(7L,
        List.of("queued"))).thenReturn(List.of(job));
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(7L,
        List.of("in_progress"))).thenReturn(List.of());

    QueueEtaService.EtaResult r = service.computeEta(job);

    assertThat(r.capacity()).isEqualTo(1);
    assertThat(r.queueAhead()).isEqualTo(1);
    assertThat(r.etaSeconds()).isNotNull();
  }

  @Test
  void runnerWithStrictSubsetLabelsIsNotCounted() {
    WorkflowJob job = queued(1L, List.of("self-hosted", "linux", "gpu"), OffsetDateTime.now());
    when(runnerRepository.findByStatus(Runner.Status.ONLINE))
        .thenReturn(List.of(
            // Missing `gpu` — should NOT be in capacity.
            runner(101L, List.of("self-hosted", "linux"), false)));
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(7L,
        List.of("queued"))).thenReturn(List.of(job));
    when(workflowJobRepository.findByRepositoryIdAndStatusInOrderByCreatedAtAsc(7L,
        List.of("in_progress"))).thenReturn(List.of());

    QueueEtaService.EtaResult r = service.computeEta(job);

    assertThat(r.capacity()).isEqualTo(0);
  }
}
