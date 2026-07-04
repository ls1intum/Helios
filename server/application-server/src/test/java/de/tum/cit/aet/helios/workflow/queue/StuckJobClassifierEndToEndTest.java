package de.tum.cit.aet.helios.workflow.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Exercises the public {@link StuckJobClassifier#classify()} entry point — finds candidate jobs,
 * sets {@code is_stuck}/{@code queued_reason}/{@code stuck_detected_at}, and persists.
 */
@ExtendWith(MockitoExtension.class)
class StuckJobClassifierEndToEndTest {

  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock RunnerRepository runnerRepository;
  @Mock GitRepoRepository repositoryRepository;
  @Mock GitHubRestClient restClient;
  @Mock WorkflowYamlCache yamlCache;
  @InjectMocks StuckJobClassifier classifier;

  private WorkflowJob queuedAndStale(Long id) {
    WorkflowJob j = new WorkflowJob();
    j.setId(id);
    j.setWorkflowRunId(99L);
    j.setRepositoryId(7L);
    j.setStatus("queued");
    j.setName("build");
    j.setRunnerKind(WorkflowJob.RunnerKind.SELF_HOSTED);
    j.setLabels(List.of("self-hosted", "linux"));
    j.setLabelSetHash(LabelSets.hash(List.of("self-hosted", "linux")));
    j.setCreatedAt(OffsetDateTime.now().minusMinutes(10));
    return j;
  }

  @Test
  void classifyPersistsReasonAndStuckFlagForEachCandidate() {
    WorkflowJob a = queuedAndStale(1L);
    WorkflowJob b = queuedAndStale(2L);
    when(workflowJobRepository.findStuckCandidates(any())).thenReturn(List.of(a, b));
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(7L);
    repo.setNameWithOwner("ls1intum/Helios");
    when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repo));
    when(restClient.get(any())).thenReturn(Optional.empty());
    when(runnerRepository.findByStatus(Runner.Status.ONLINE)).thenReturn(List.of());

    classifier.classify();

    ArgumentCaptor<WorkflowJob> savedJob = ArgumentCaptor.forClass(WorkflowJob.class);
    verify(workflowJobRepository, org.mockito.Mockito.times(2)).save(savedJob.capture());
    assertThat(savedJob.getAllValues()).allSatisfy(job -> {
      assertThat(job.isStuck()).isTrue();
      assertThat(job.getStuckDetectedAt()).isNotNull();
      assertThat(job.getQueuedReason()).isEqualTo(WorkflowJob.QueuedReason.NO_RUNNER_ONLINE);
    });
  }

  @Test
  void classifyIsNoopWhenNoCandidates() {
    when(workflowJobRepository.findStuckCandidates(any())).thenReturn(List.of());

    classifier.classify();

    verify(workflowJobRepository, never()).save(any());
  }
}
