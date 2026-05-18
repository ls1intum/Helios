package de.tum.cit.aet.helios.workflow.queue;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowJobPayload;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Caffeine-backed hot index of recent queue activity per (repository, label-set hash).
 *
 * <p>Read by the dashboard for sub-100ms queue-depth responses; truth source is {@code
 * workflow_job} table. See plan §C1.
 */
@Service
@Log4j2
public class QueueIndexService {

  /** key = repositoryId + ":" + labelSetHash → atomic queued-count snapshot. */
  private final Cache<String, AtomicInteger> queuedByLabelSet =
      Caffeine.newBuilder()
          .expireAfterWrite(Duration.ofMinutes(15))
          .maximumSize(10_000)
          .build();

  public void onWorkflowJobEvent(GitHubWorkflowJobPayload payload) {
    if (payload == null || payload.workflowJob() == null || payload.repository() == null) {
      return;
    }
    GitHubWorkflowJobPayload.WorkflowJob job = payload.workflowJob();
    if (job.id() == null) {
      return;
    }
    String hash = LabelSets.hash(job.labels());
    String key = payload.repository().id() + ":" + hash;
    String status = job.status() == null ? "" : job.status().toLowerCase();

    AtomicInteger counter =
        queuedByLabelSet.get(key, k -> new AtomicInteger(0));

    switch (status) {
      case "queued" -> counter.incrementAndGet();
      case "in_progress", "completed" -> {
        if (counter.get() > 0) {
          counter.decrementAndGet();
        }
      }
      default -> {
        // No-op for unknown statuses.
      }
    }
    log.debug("queue-index {} status={} count={}", key, status, counter.get());
  }

  /** Snapshot of queued counts by (repoId, labelSetHash). */
  public Map<String, Integer> snapshot() {
    Map<String, Integer> out = new ConcurrentHashMap<>();
    queuedByLabelSet
        .asMap()
        .forEach((k, v) -> out.put(k, v.get()));
    return out;
  }

  public int snapshotFor(Long repositoryId, List<String> labels) {
    String key = repositoryId + ":" + LabelSets.hash(labels);
    AtomicInteger counter = queuedByLabelSet.getIfPresent(key);
    return counter == null ? 0 : counter.get();
  }
}
