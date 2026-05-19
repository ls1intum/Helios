package de.tum.cit.aet.helios.workflow.queue;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Classifies why a queued job is stuck. Ordering matters; first match wins. See plan §C2.
 */
@Service
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(name = "helios.queue.enabled", havingValue = "true")
public class StuckJobClassifier {

  private final WorkflowJobRepository workflowJobRepository;
  private final RunnerRepository runnerRepository;
  private final GitRepoRepository repositoryRepository;
  private final GitHubRestClient restClient;
  private final WorkflowYamlCache yamlCache;

  @Scheduled(fixedRateString = "${helios.queue.reconcile.stuck.fixedRateMs:60000}")
  @Transactional
  public void classify() {
    OffsetDateTime before = OffsetDateTime.now().minusMinutes(3);
    List<WorkflowJob> candidates = workflowJobRepository.findStuckCandidates(before);
    if (candidates.isEmpty()) {
      return;
    }
    for (WorkflowJob job : candidates) {
      WorkflowJob.QueuedReason reason = classify(job);
      job.setQueuedReason(reason);
      job.setStuck(true);
      if (job.getStuckDetectedAt() == null) {
        job.setStuckDetectedAt(OffsetDateTime.now());
      }
      workflowJobRepository.save(job);
    }
  }

  WorkflowJob.QueuedReason classify(WorkflowJob job) {
    if (isPendingApproval(job)) {
      return WorkflowJob.QueuedReason.PENDING_APPROVAL;
    }
    if (job.getRunnerKind() == WorkflowJob.RunnerKind.SELF_HOSTED) {
      List<Runner> matching = matchingRunners(job);
      if (matching.isEmpty()) {
        return WorkflowJob.QueuedReason.NO_RUNNER_ONLINE;
      }
      boolean anyIdle = matching.stream().anyMatch(r -> !r.isBusy());
      if (!anyIdle) {
        return WorkflowJob.QueuedReason.RUNNERS_BUSY;
      }
    }
    if (hasConcurrencyLock(job)) {
      return WorkflowJob.QueuedReason.CONCURRENCY_LOCK;
    }
    return WorkflowJob.QueuedReason.UNKNOWN;
  }

  private boolean isPendingApproval(WorkflowJob job) {
    Optional<GitRepository> repoOpt = repositoryRepository.findById(job.getRepositoryId());
    if (repoOpt.isEmpty()) {
      return false;
    }
    String fullName = repoOpt.get().getNameWithOwner();
    Optional<JsonNode> run =
        restClient.get("/repos/" + fullName + "/actions/runs/" + job.getWorkflowRunId());
    if (run.isPresent() && "waiting".equalsIgnoreCase(run.get().path("status").asText(""))) {
      return true;
    }
    Optional<JsonNode> pending = restClient.get(
        "/repos/" + fullName + "/actions/runs/" + job.getWorkflowRunId()
            + "/pending_deployments");
    return pending.isPresent() && pending.get().isArray() && pending.get().size() > 0;
  }

  private List<Runner> matchingRunners(WorkflowJob job) {
    List<Runner> online = runnerRepository.findByStatus(Runner.Status.ONLINE);
    List<String> needed = job.getLabels() == null ? List.of() : job.getLabels();
    return online.stream()
        .filter(r -> r.getLabels() != null
            && r.getLabels().stream().map(String::toLowerCase).toList().containsAll(needed))
        .toList();
  }

  private boolean hasConcurrencyLock(WorkflowJob job) {
    Optional<GitRepository> repoOpt = repositoryRepository.findById(job.getRepositoryId());
    if (repoOpt.isEmpty() || job.getHeadSha() == null || job.getWorkflowName() == null) {
      return false;
    }
    String fullName = repoOpt.get().getNameWithOwner();
    // Workflow file path isn't always known from the job alone; conservative path guess.
    Optional<WorkflowYamlCache.WorkflowYaml> yaml =
        yamlCache.fetch(fullName, job.getHeadSha(),
            ".github/workflows/" + slug(job.getWorkflowName()) + ".yml");
    return yaml.isPresent() && yaml.get().concurrencyGroupExpression() != null;
  }

  private String slug(String workflowName) {
    return workflowName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }
}
