package de.tum.cit.aet.helios.workflow.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StuckJobClassifierTest {

  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock RunnerRepository runnerRepository;
  @Mock GitRepoRepository repositoryRepository;
  @Mock GitHubRestClient restClient;
  @Mock WorkflowYamlCache yamlCache;

  @InjectMocks StuckJobClassifier classifier;

  private final ObjectMapper om = new ObjectMapper();

  private WorkflowJob job(WorkflowJob.RunnerKind kind, List<String> labels) {
    WorkflowJob j = new WorkflowJob();
    j.setId(1L);
    j.setWorkflowRunId(42L);
    j.setRepositoryId(7L);
    j.setRunnerKind(kind);
    j.setLabels(labels);
    j.setLabelSetHash(LabelSets.hash(labels));
    j.setStatus("queued");
    j.setHeadSha("abc");
    j.setWorkflowName("CI");
    j.setCreatedAt(OffsetDateTime.now().minusMinutes(10));
    return j;
  }

  private GitRepository repo() {
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(7L);
    repo.setNameWithOwner("ls1intum/Helios");
    return repo;
  }

  private WorkflowJob.QueuedReason classify(WorkflowJob j) throws Exception {
    Method m = StuckJobClassifier.class.getDeclaredMethod("classify", WorkflowJob.class);
    m.setAccessible(true);
    return (WorkflowJob.QueuedReason) m.invoke(classifier, j);
  }

  @Test
  void noRunnerOnlineWhenSelfHostedAndNoMatchingRunners() throws Exception {
    when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repo()));
    when(restClient.get(any())).thenReturn(Optional.empty());
    when(runnerRepository.findByStatus(Runner.Status.ONLINE)).thenReturn(List.of());

    WorkflowJob j = job(WorkflowJob.RunnerKind.SELF_HOSTED, List.of("self-hosted", "linux"));
    assertThat(classify(j)).isEqualTo(WorkflowJob.QueuedReason.NO_RUNNER_ONLINE);
  }

  @Test
  void runnersBusyWhenAllMatchingRunnersAreBusy() throws Exception {
    when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repo()));
    when(restClient.get(any())).thenReturn(Optional.empty());

    Runner r = new Runner();
    r.setLabels(List.of("self-hosted", "linux", "x64"));
    r.setStatus(Runner.Status.ONLINE);
    r.setBusy(true);
    when(runnerRepository.findByStatus(Runner.Status.ONLINE)).thenReturn(List.of(r));

    WorkflowJob j = job(WorkflowJob.RunnerKind.SELF_HOSTED, List.of("self-hosted", "linux"));
    assertThat(classify(j)).isEqualTo(WorkflowJob.QueuedReason.RUNNERS_BUSY);
  }

  @Test
  void pendingApprovalWhenRunStatusWaiting() throws Exception {
    when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repo()));
    ObjectNode runNode = om.createObjectNode();
    runNode.put("status", "waiting");
    when(restClient.get(eq("/repos/ls1intum/Helios/actions/runs/42")))
        .thenReturn(Optional.of(runNode));

    WorkflowJob j = job(WorkflowJob.RunnerKind.SELF_HOSTED, List.of("self-hosted", "linux"));
    assertThat(classify(j)).isEqualTo(WorkflowJob.QueuedReason.PENDING_APPROVAL);
  }

  @Test
  void pendingApprovalWhenPendingDeploymentsNonEmpty() throws Exception {
    when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repo()));
    ObjectNode runNode = om.createObjectNode();
    runNode.put("status", "queued");
    when(restClient.get(eq("/repos/ls1intum/Helios/actions/runs/42")))
        .thenReturn(Optional.of(runNode));
    ArrayNode pending = om.createArrayNode();
    pending.add(om.createObjectNode());
    when(restClient.get(eq("/repos/ls1intum/Helios/actions/runs/42/pending_deployments")))
        .thenReturn(Optional.of(pending));

    WorkflowJob j = job(WorkflowJob.RunnerKind.SELF_HOSTED, List.of("self-hosted", "linux"));
    assertThat(classify(j)).isEqualTo(WorkflowJob.QueuedReason.PENDING_APPROVAL);
  }

  @Test
  void unknownReasonForGithubHostedFallthrough() throws Exception {
    when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repo()));
    when(restClient.get(any())).thenReturn(Optional.empty());
    when(yamlCache.fetch(any(), any(), any())).thenReturn(Optional.empty());

    WorkflowJob j = job(WorkflowJob.RunnerKind.GITHUB_HOSTED, List.of("ubuntu-latest"));
    assertThat(classify(j)).isEqualTo(WorkflowJob.QueuedReason.UNKNOWN);
  }
}
