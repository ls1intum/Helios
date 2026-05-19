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
 * Caffeine-backed hot index of recent queue activity per (repository, label-set hash). Tracks
 * per-job state so redelivered webhooks don't drift the counter. See plan §C1.
 */
@Service
@Log4j2
public class QueueIndexService {

  /** Per-(repoId:hash) counter snapshot. */
  private final Cache<String, AtomicInteger> queuedByLabelSet =
      Caffeine.newBuilder()
          .expireAfterWrite(Duration.ofHours(2))
          .maximumSize(10_000)
          .build();

  /**
   * Last observed state per job id. Caffeine entries time out after 4h so we don't accumulate
   * forever; webhooks fire faster than that for any active job.
   */
  private final Cache<Long, JobState> jobState =
      Caffeine.newBuilder()
          .expireAfterAccess(Duration.ofHours(4))
          .maximumSize(50_000)
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

    JobState newState = JobState.fromStatus(status);
    JobState prev = jobState.getIfPresent(job.id());

    if (newState == prev) {
      // Redelivery of the same state — counter must not move.
      log.trace("queue-index redelivery for job {} status={} (no-op)", job.id(), status);
      return;
    }

    AtomicInteger counter = queuedByLabelSet.get(key, k -> new AtomicInteger(0));
    // Apply the transition: only QUEUED contributes to the counter.
    if (newState == JobState.QUEUED) {
      counter.incrementAndGet();
    } else if (prev == JobState.QUEUED) {
      // Leaving QUEUED (→ IN_PROGRESS / COMPLETED / OTHER).
      if (counter.get() > 0) {
        counter.decrementAndGet();
      }
    }

    if (newState == JobState.COMPLETED) {
      jobState.invalidate(job.id()); // Don't retain after completion.
    } else {
      jobState.put(job.id(), newState);
    }
    log.debug("queue-index {} prev={} new={} count={}", key, prev, newState, counter.get());
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

  /**
   * Coarse job-state classification — collapses GitHub's vocabulary to the three buckets the
   * counter cares about.
   */
  enum JobState {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    OTHER;

    static JobState fromStatus(String status) {
      return switch (status == null ? "" : status.toLowerCase()) {
        case "queued", "waiting" -> QUEUED;
        case "in_progress" -> IN_PROGRESS;
        case "completed" -> COMPLETED;
        default -> OTHER;
      };
    }
  }
}
