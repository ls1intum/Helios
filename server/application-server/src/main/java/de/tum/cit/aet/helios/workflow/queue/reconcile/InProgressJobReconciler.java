package de.tum.cit.aet.helios.workflow.queue.reconcile;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.workflow.queue.LabelSets;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJob;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Fills in {@code runner_id} / {@code labels} for jobs the webhook missed. Backs off via {@code
 * last_reconcile_attempt_at}. See plan §B5.
 */
@Service
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(name = "helios.queue.enabled", havingValue = "true")
public class InProgressJobReconciler {

  private final WorkflowJobRepository workflowJobRepository;
  private final GitRepoRepository repositoryRepository;
  private final GitHubRestClient restClient;

  @Scheduled(fixedRateString = "${helios.queue.reconcile.jobs.fixedRateMs:30000}")
  @Transactional
  public void reconcile() {
    OffsetDateTime before = OffsetDateTime.now().minusSeconds(60);
    OffsetDateTime backoffBefore = OffsetDateTime.now().minusMinutes(5);
    List<WorkflowJob> jobs =
        workflowJobRepository.findJobsNeedingRunnerReconciliation(before, backoffBefore);
    if (jobs.isEmpty()) {
      return;
    }

    Set<Long> runsHandled = new HashSet<>();
    List<Long> attemptedJobIds = new ArrayList<>();

    for (WorkflowJob job : jobs) {
      attemptedJobIds.add(job.getId());
      if (!runsHandled.add(job.getWorkflowRunId())) {
        continue;
      }
      Optional<GitRepository> repoOpt =
          repositoryRepository.findById(job.getRepositoryId());
      if (repoOpt.isEmpty()) {
        continue;
      }
      String fullName = repoOpt.get().getNameWithOwner();
      String path =
          "/repos/" + fullName + "/actions/runs/" + job.getWorkflowRunId() + "/jobs?per_page=100";
      Optional<JsonNode> body = restClient.get(path);
      if (body.isEmpty()) {
        continue;
      }
      JsonNode list = body.get().get("jobs");
      if (list == null || !list.isArray()) {
        continue;
      }
      for (JsonNode node : list) {
        if (!node.hasNonNull("id")) {
          continue;
        }
        Long id = node.get("id").asLong();
        Optional<WorkflowJob> wjOpt = workflowJobRepository.findById(id);
        if (wjOpt.isEmpty()) {
          continue;
        }
        WorkflowJob wj = wjOpt.get();
        if (node.hasNonNull("runner_id")) {
          wj.setRunnerId(node.get("runner_id").asLong());
        }
        if (node.hasNonNull("runner_name")) {
          wj.setRunnerName(node.get("runner_name").asText());
        }
        if (node.hasNonNull("runner_group_id")) {
          wj.setRunnerGroupId(node.get("runner_group_id").asLong());
        }
        if (node.hasNonNull("runner_group_name")) {
          wj.setRunnerGroupName(node.get("runner_group_name").asText());
        }
        JsonNode labels = node.get("labels");
        if (labels != null && labels.isArray()) {
          List<String> labelNames = new ArrayList<>();
          for (JsonNode l : labels) {
            if (l.isTextual()) {
              labelNames.add(l.asText());
            }
          }
          if (!labelNames.isEmpty()) {
            List<String> canonical = LabelSets.canonical(labelNames);
            wj.setLabels(canonical);
            wj.setLabelSetHash(LabelSets.hash(canonical));
            wj.setRunnerKind(LabelSets.deriveRunnerKind(canonical));
          }
        }
        workflowJobRepository.save(wj);
      }
    }

    if (!attemptedJobIds.isEmpty()) {
      workflowJobRepository.touchReconcileAttempt(attemptedJobIds, OffsetDateTime.now());
    }
  }
}
