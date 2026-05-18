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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * One-shot 30-day backfill triggered by admin endpoint. Self-throttles to a safe req/min budget.
 * See plan §B5.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class WorkflowJobBackfillService {

  private final GitRepoRepository repositoryRepository;
  private final WorkflowJobRepository workflowJobRepository;
  private final GitHubRestClient restClient;

  private final AtomicBoolean running = new AtomicBoolean(false);

  /** Returns true if a new backfill was started, false if one is already running. */
  public boolean start() {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    runAsync();
    return true;
  }

  public boolean isRunning() {
    return running.get();
  }

  @Async
  protected void runAsync() {
    try {
      backfillAll();
    } finally {
      running.set(false);
    }
  }

  @Transactional
  protected void backfillAll() {
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
    String sinceStr = ">=" + since.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    long minIntervalMs = 60_000L / 180L; // 180 req/min self-throttle
    long lastCall = 0L;

    for (GitRepository repo : repositoryRepository.findAll()) {
      String fullName = repo.getNameWithOwner();
      log.info("Backfill: starting repo {}", fullName);
      int page = 1;
      while (true) {
        long now = System.currentTimeMillis();
        long sleepFor = Math.max(0L, minIntervalMs - (now - lastCall));
        if (sleepFor > 0) {
          try {
            Thread.sleep(sleepFor);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
          }
        }
        lastCall = System.currentTimeMillis();

        String path = "/repos/" + fullName + "/actions/runs?per_page=100&page=" + page
            + "&created=" + sinceStr;
        Optional<JsonNode> body = restClient.get(path);
        if (body.isEmpty()) {
          break;
        }
        JsonNode runs = body.get().get("workflow_runs");
        if (runs == null || !runs.isArray() || runs.isEmpty()) {
          break;
        }
        for (JsonNode run : runs) {
          if (!run.hasNonNull("id")) {
            continue;
          }
          Long runId = run.get("id").asLong();
          ingestRunJobs(fullName, runId, repo.getRepositoryId());
        }
        if (runs.size() < 100) {
          break;
        }
        page++;
      }
    }
  }

  private void ingestRunJobs(String fullName, Long runId, Long repositoryId) {
    String path = "/repos/" + fullName + "/actions/runs/" + runId + "/jobs?per_page=100";
    Optional<JsonNode> body = restClient.get(path);
    if (body.isEmpty()) {
      return;
    }
    JsonNode jobs = body.get().get("jobs");
    if (jobs == null || !jobs.isArray()) {
      return;
    }
    for (JsonNode node : jobs) {
      if (!node.hasNonNull("id")) {
        continue;
      }
      Long id = node.get("id").asLong();
      WorkflowJob job = workflowJobRepository.findById(id).orElseGet(WorkflowJob::new);
      job.setId(id);
      job.setWorkflowRunId(runId);
      job.setRepositoryId(repositoryId);
      job.setName(text(node, "name", ""));
      job.setWorkflowName(textOrNull(node, "workflow_name"));
      job.setHeadBranch(textOrNull(node, "head_branch"));
      job.setHeadSha(textOrNull(node, "head_sha"));
      job.setStatus(text(node, "status", "completed"));
      job.setConclusion(textOrNull(node, "conclusion"));
      if (node.hasNonNull("created_at")) {
        job.setCreatedAt(OffsetDateTime.parse(node.get("created_at").asText()));
      }
      if (node.hasNonNull("started_at")) {
        job.setStartedAt(OffsetDateTime.parse(node.get("started_at").asText()));
      }
      if (node.hasNonNull("completed_at")) {
        job.setCompletedAt(OffsetDateTime.parse(node.get("completed_at").asText()));
      }
      JsonNode labels = node.get("labels");
      List<String> labelNames = new ArrayList<>();
      if (labels != null && labels.isArray()) {
        for (JsonNode l : labels) {
          if (l.isTextual()) {
            labelNames.add(l.asText());
          }
        }
      }
      List<String> canonical = LabelSets.canonical(labelNames);
      job.setLabels(canonical);
      job.setLabelSetHash(LabelSets.hash(canonical));
      job.setRunnerKind(LabelSets.deriveRunnerKind(canonical));
      if (node.hasNonNull("runner_id")) {
        job.setRunnerId(node.get("runner_id").asLong());
      }
      job.setRunnerName(textOrNull(node, "runner_name"));
      if (job.getStartedAt() != null && job.getCreatedAt() != null) {
        job.setQueueWaitSeconds(
            (int) Math.max(0L, job.getStartedAt().toEpochSecond() - job.getCreatedAt().toEpochSecond()));
      }
      if (job.getCompletedAt() != null && job.getStartedAt() != null) {
        job.setRunDurationSeconds(
            (int) Math.max(0L, job.getCompletedAt().toEpochSecond() - job.getStartedAt().toEpochSecond()));
      }
      workflowJobRepository.save(job);
    }
  }

  private String text(JsonNode node, String field, String fallback) {
    return node.hasNonNull(field) ? node.get(field).asText() : fallback;
  }

  private String textOrNull(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }
}
