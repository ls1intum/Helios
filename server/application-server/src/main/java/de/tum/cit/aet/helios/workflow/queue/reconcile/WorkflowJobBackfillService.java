package de.tum.cit.aet.helios.workflow.queue.reconcile;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.queue.LabelSets;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJob;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import jakarta.transaction.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * One-shot 30-day backfill triggered by admin endpoint. Self-throttles to a safe req/min budget,
 * paginates {@code /actions/runs} and {@code /actions/runs/{id}/jobs}, and asks the
 * {@link QueueWaitStatRollup} to materialise historical buckets when done.
 *
 * <p>The async dispatch happens via {@link WorkflowJobBackfillExecutor} so Spring's AOP proxy
 * intercepts {@code @Async} (self-invocation would not).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class WorkflowJobBackfillService {

  private final GitRepoRepository repositoryRepository;
  private final WorkflowJobRepository workflowJobRepository;
  private final GitHubRestClient restClient;
  private final QueueWaitStatRollup rollup;
  private final ApplicationContext context;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean aborted = new AtomicBoolean(false);

  /** Returns true if a new backfill was started, false if one is already running. */
  public boolean start() {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    aborted.set(false);
    // Dispatch through the proxied executor so @Async actually runs on a worker thread.
    context.getBean(WorkflowJobBackfillExecutor.class).runAsync();
    return true;
  }

  /** Signal the running backfill to stop after the current page. */
  public void abort() {
    aborted.set(true);
  }

  public boolean isRunning() {
    return running.get();
  }

  /** Called from {@link WorkflowJobBackfillExecutor#runAsync()} — runs on the async pool. */
  public void runBackfill() {
    try {
      OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
      String sinceParam = "%3E%3D" + URLEncoder.encode(
          since.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), StandardCharsets.UTF_8);
      long minIntervalMs = 60_000L / 180L; // 180 req/min self-throttle
      long lastCall = 0L;

      for (GitRepository repo : repositoryRepository.findAll()) {
        if (aborted.get()) {
          log.info("Backfill aborted");
          break;
        }
        String fullName = repo.getNameWithOwner();
        log.info("Backfill: starting repo {}", fullName);
        int page = 1;
        while (!aborted.get()) {
          lastCall = throttle(lastCall, minIntervalMs);
          String path = "/repos/" + fullName + "/actions/runs?per_page=100&page=" + page
              + "&created=" + sinceParam;
          Optional<JsonNode> body = restClient.get(path);
          if (body.isEmpty()) {
            break;
          }
          JsonNode runs = body.get().get("workflow_runs");
          if (runs == null || !runs.isArray() || runs.isEmpty()) {
            break;
          }
          for (JsonNode run : runs) {
            if (aborted.get()) {
              break;
            }
            if (!run.hasNonNull("id")) {
              continue;
            }
            Long runId = run.get("id").asLong();
            String workflowName = textOrNull(run, "name");
            String headBranch = textOrNull(run, "head_branch");
            String headSha = textOrNull(run, "head_sha");
            ingestRunJobs(fullName, runId, repo.getRepositoryId(), workflowName, headBranch,
                headSha);
          }
          if (runs.size() < 100) {
            break;
          }
          page++;
        }
      }
      if (!aborted.get()) {
        // Materialise historical hourly buckets so /stats has data immediately.
        rollup.rollupRange(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30),
            OffsetDateTime.now(ZoneOffset.UTC));
      }
    } finally {
      running.set(false);
    }
  }

  public void ingestRunJobs(String fullName, Long runId, Long repositoryId,
      String workflowName, String headBranch, String headSha) {
    int page = 1;
    while (!aborted.get()) {
      String path =
          "/repos/" + fullName + "/actions/runs/" + runId + "/jobs?per_page=100&page=" + page;
      Optional<JsonNode> body = restClient.get(path);
      if (body.isEmpty()) {
        return;
      }
      JsonNode jobs = body.get().get("jobs");
      if (jobs == null || !jobs.isArray() || jobs.isEmpty()) {
        return;
      }
      // Persist the page through the Spring proxy so @Transactional applies (a direct
      // self-invocation would bypass the proxy). The transaction wraps only the DB writes — never
      // the GitHub pagination above — so a connection is not held open across network I/O.
      context.getBean(WorkflowJobBackfillService.class)
          .saveJobPage(jobs, runId, repositoryId, workflowName, headBranch, headSha);
      if (jobs.size() < 100) {
        return;
      }
      page++;
    }
  }

  @Transactional
  public void saveJobPage(JsonNode jobs, Long runId, Long repositoryId,
      String workflowName, String headBranch, String headSha) {
    for (JsonNode node : jobs) {
      if (!node.hasNonNull("id")) {
        continue;
      }
      saveJob(node, runId, repositoryId, workflowName, headBranch, headSha);
    }
  }

  private void saveJob(JsonNode node, Long runId, Long repositoryId, String workflowName,
      String headBranch, String headSha) {
    Long id = node.get("id").asLong();
    WorkflowJob job = workflowJobRepository.findById(id).orElseGet(WorkflowJob::new);
    job.setId(id);
    job.setWorkflowRunId(runId);
    job.setRepositoryId(repositoryId);
    job.setName(text(node, "name", ""));
    // Job payload may not carry these; inherit from the run row we paginated above.
    String wfName = textOrNull(node, "workflow_name");
    job.setWorkflowName(wfName != null ? wfName : workflowName);
    String branch = textOrNull(node, "head_branch");
    job.setHeadBranch(branch != null ? branch : headBranch);
    String sha = textOrNull(node, "head_sha");
    job.setHeadSha(sha != null ? sha : headSha);
    String status = text(node, "status", "completed").toLowerCase();
    job.setStatus(status);
    String conclusion = textOrNull(node, "conclusion");
    job.setConclusion(conclusion == null ? null : conclusion.toLowerCase());
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
      job.setQueueWaitSeconds((int) Math.max(0L,
          job.getStartedAt().toEpochSecond() - job.getCreatedAt().toEpochSecond()));
    }
    if (job.getCompletedAt() != null && job.getStartedAt() != null) {
      job.setRunDurationSeconds((int) Math.max(0L,
          job.getCompletedAt().toEpochSecond() - job.getStartedAt().toEpochSecond()));
    }
    workflowJobRepository.save(job);
  }

  private long throttle(long lastCall, long minIntervalMs) {
    long now = System.currentTimeMillis();
    long sleepFor = Math.max(0L, minIntervalMs - (now - lastCall));
    if (sleepFor > 0) {
      try {
        Thread.sleep(sleepFor);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
    return System.currentTimeMillis();
  }

  private String text(JsonNode node, String field, String fallback) {
    return node.hasNonNull(field) ? node.get(field).asText() : fallback;
  }

  private String textOrNull(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }
}
