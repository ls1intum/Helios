package de.tum.cit.aet.helios.workflow.queue.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJob;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
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
 * Exercises the full happy path of {@link InProgressJobReconciler} — REST call fired, runner
 * details filled in, {@code last_reconcile_attempt_at} touched. The companion
 * {@link InProgressJobReconcilerTest} only covers no-op branches.
 */
@ExtendWith(MockitoExtension.class)
class InProgressJobReconcilerFullPathTest {

  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock GitRepoRepository repositoryRepository;
  @Mock GitHubRestClient restClient;
  @InjectMocks InProgressJobReconciler reconciler;

  private final ObjectMapper om = new ObjectMapper();

  private WorkflowJob queuedWithoutRunner() {
    WorkflowJob j = new WorkflowJob();
    j.setId(42L);
    j.setWorkflowRunId(99L);
    j.setRepositoryId(7L);
    j.setName("build");
    j.setStatus("queued");
    j.setCreatedAt(OffsetDateTime.now().minusMinutes(2));
    return j;
  }

  private GitRepository repository() {
    GitRepository r = new GitRepository();
    r.setRepositoryId(7L);
    r.setNameWithOwner("ls1intum/Helios");
    return r;
  }

  private ObjectNode jobsResponse(long jobId, long runnerId, String runnerName,
      List<String> labels) {
    ObjectNode body = om.createObjectNode();
    ArrayNode jobs = body.putArray("jobs");
    ObjectNode job = jobs.addObject();
    job.put("id", jobId);
    job.put("runner_id", runnerId);
    job.put("runner_name", runnerName);
    job.put("runner_group_name", "default");
    ArrayNode labelsArr = job.putArray("labels");
    labels.forEach(labelsArr::add);
    return body;
  }

  @Test
  void fillsRunnerDetailsFromRestResponse() {
    WorkflowJob job = queuedWithoutRunner();
    when(workflowJobRepository.findJobsNeedingRunnerReconciliation(any(), any()))
        .thenReturn(List.of(job));
    when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repository()));
    // Page 1 returns one job; loop exits because list.size() < 100.
    when(restClient.get(eq("/repos/ls1intum/Helios/actions/runs/99/jobs?per_page=100&page=1")))
        .thenReturn(Optional.of(jobsResponse(42L, 101L, "runner-1",
            List.of("self-hosted", "linux"))));
    when(workflowJobRepository.findById(42L)).thenReturn(Optional.of(job));

    reconciler.reconcile();

    ArgumentCaptor<WorkflowJob> captor = ArgumentCaptor.forClass(WorkflowJob.class);
    verify(workflowJobRepository).save(captor.capture());
    WorkflowJob saved = captor.getValue();
    assertThat(saved.getRunnerId()).isEqualTo(101L);
    assertThat(saved.getRunnerName()).isEqualTo("runner-1");
    assertThat(saved.getRunnerGroupName()).isEqualTo("default");
    assertThat(saved.getLabels()).contains("self-hosted", "linux");
    assertThat(saved.getRunnerKind()).isEqualTo(WorkflowJob.RunnerKind.SELF_HOSTED);
    assertThat(saved.getLabelSetHash()).isNotBlank();
  }

  @Test
  void touchesReconcileAttemptEvenWhenRestReturnsEmpty() {
    WorkflowJob job = queuedWithoutRunner();
    when(workflowJobRepository.findJobsNeedingRunnerReconciliation(any(), any()))
        .thenReturn(List.of(job));
    when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repository()));
    when(restClient.get(any())).thenReturn(Optional.empty()); // 304 or transient error

    reconciler.reconcile();

    verify(workflowJobRepository).touchReconcileAttempt(anyList(), any());
    // No save() because there was nothing to update.
    verify(workflowJobRepository, never()).save(any());
  }

  @Test
  void onlyOneRestCallPerUniqueWorkflowRun() {
    // Two jobs in the same workflow run → only one /actions/runs/{id}/jobs call.
    WorkflowJob jobA = queuedWithoutRunner();
    jobA.setId(42L);
    WorkflowJob jobB = queuedWithoutRunner();
    jobB.setId(43L);
    when(workflowJobRepository.findJobsNeedingRunnerReconciliation(any(), any()))
        .thenReturn(List.of(jobA, jobB));
    when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repository()));
    when(restClient.get(any())).thenReturn(Optional.empty());

    reconciler.reconcile();

    verify(restClient, times(1)).get(any());
    // Both ids must appear in the touch call.
    ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass((Class) List.class);
    verify(workflowJobRepository).touchReconcileAttempt(ids.capture(), any());
    assertThat(ids.getValue()).containsExactly(42L, 43L);
  }
}
