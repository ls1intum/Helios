package de.tum.cit.aet.helios.workflow.queue;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ETA computation with label-superset capacity. See plan §C3.
 *
 * <p>Cached per <em>job id</em> for 3 s (the result depends on the specific job's position in the
 * queue, not just its label set). GitHub-hosted returns no ETA; only a saturation badge based on a
 * configurable concurrency ceiling.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class QueueEtaService {

  private final WorkflowJobRepository workflowJobRepository;
  private final RunnerRepository runnerRepository;
  private final QueueWaitStatRepository statsRepository;

  @Value("${helios.queue.eta.githubHostedConcurrencyCeiling:20}")
  private int githubHostedCeiling;

  /** Cache key = job id. Reusing across jobs is wrong because position-in-queue differs. */
  private final Cache<Long, EtaResult> etaCache =
      Caffeine.newBuilder()
          .expireAfterWrite(Duration.ofSeconds(3))
          .maximumSize(10_000)
          .build();

  public EtaResult computeEta(WorkflowJob job) {
    if (job == null || job.getId() == null) {
      return new EtaResult(null, null, null, null, null, false);
    }
    EtaResult cached = etaCache.getIfPresent(job.getId());
    if (cached != null) {
      return cached;
    }
    EtaResult result = computeUncached(job);
    etaCache.put(job.getId(), result);
    return result;
  }

  private EtaResult computeUncached(WorkflowJob job) {
    if (job.getRunnerKind() == WorkflowJob.RunnerKind.GITHUB_HOSTED) {
      return computeGitHubHosted(job);
    }
    return computeSelfHosted(job);
  }

  private EtaResult computeGitHubHosted(WorkflowJob job) {
    List<WorkflowJob> active = workflowJobRepository
        .findByRepositoryIdAndStatusInOrderByCreatedAtAsc(
            job.getRepositoryId(), List.of("queued", "in_progress"));
    long ghhActive = active.stream()
        .filter(j -> j.getRunnerKind() == WorkflowJob.RunnerKind.GITHUB_HOSTED).count();
    double saturation = githubHostedCeiling <= 0 ? 0.0 : (double) ghhActive / githubHostedCeiling;
    Integer p50 = lookupQueueP50(job);
    return new EtaResult(null, null, null, saturation, p50, saturation > 0.7);
  }

  private EtaResult computeSelfHosted(WorkflowJob job) {
    List<Runner> online = runnerRepository.findByStatus(Runner.Status.ONLINE);
    List<String> needed = lowercase(job.getLabels());
    List<Runner> competing = online.stream()
        .filter(r -> hasLabels(r.getLabels(), needed))
        .toList();
    int capacity = competing.size();
    if (capacity == 0) {
      // No runner could ever pick this job up — don't pretend it's schedulable.
      return new EtaResult(null, 0, null, null, null, false);
    }

    Integer p50run = lookupRunP50(job);
    if (p50run == null) {
      p50run = medianRunDuration(job.getRepositoryId());
    }
    if (p50run == null) {
      p50run = 0;
    }

    List<WorkflowJob> queuedAhead = workflowJobRepository
        .findByRepositoryIdAndStatusInOrderByCreatedAtAsc(job.getRepositoryId(), List.of("queued"))
        .stream()
        // Strictly before this job — exclude the job being estimated.
        .filter(q -> q.getCreatedAt() != null
            && job.getCreatedAt() != null
            && q.getCreatedAt().isBefore(job.getCreatedAt())
            && !q.getId().equals(job.getId()))
        .filter(q -> jobCanRunOnAnyCompeting(q, competing))
        .toList();
    int queueAhead = queuedAhead.size();

    List<WorkflowJob> activeJobs = workflowJobRepository
        .findByRepositoryIdAndStatusInOrderByCreatedAtAsc(
            job.getRepositoryId(), List.of("in_progress"))
        .stream()
        .filter(j -> jobCanRunOnAnyCompeting(j, competing))
        .toList();

    long remainingRunningSum = 0L;
    OffsetDateTime now = OffsetDateTime.now();
    for (WorkflowJob active : activeJobs) {
      OffsetDateTime started = active.getStartedAt();
      if (started == null) {
        continue;
      }
      long elapsed = Math.max(0L, now.toEpochSecond() - started.toEpochSecond());
      long remaining = Math.max(0L, (long) p50run - elapsed);
      remainingRunningSum += remaining;
    }
    long remainingRunning = remainingRunningSum / capacity;
    int slotsAhead = (int) Math.ceil((double) queueAhead / capacity);
    long eta = (long) slotsAhead * p50run + remainingRunning;

    return new EtaResult(eta, capacity, queueAhead, null, null, false);
  }

  private boolean jobCanRunOnAnyCompeting(WorkflowJob job, List<Runner> competing) {
    if (job.getLabels() == null || job.getLabels().isEmpty()) {
      return !competing.isEmpty();
    }
    List<String> needed = lowercase(job.getLabels());
    for (Runner r : competing) {
      if (hasLabels(r.getLabels(), needed)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasLabels(List<String> runnerLabels, List<String> needed) {
    if (runnerLabels == null) {
      return needed.isEmpty();
    }
    List<String> lc = lowercase(runnerLabels);
    return lc.containsAll(needed);
  }

  private List<String> lowercase(List<String> in) {
    if (in == null) {
      return List.of();
    }
    return in.stream().map(s -> s == null ? "" : s.toLowerCase(Locale.ROOT)).toList();
  }

  private Integer lookupQueueP50(WorkflowJob job) {
    Optional<QueueWaitStat> recent = statsRepository
        .findForWindow(job.getRepositoryId(), job.getWorkflowName(), job.getName(),
            job.getHeadBranch(), OffsetDateTime.now().minusDays(7))
        .stream()
        .reduce((a, b) -> b);
    return recent.map(QueueWaitStat::getQueueP50).orElse(null);
  }

  private Integer lookupRunP50(WorkflowJob job) {
    Optional<QueueWaitStat> recent = statsRepository
        .findForWindow(job.getRepositoryId(), job.getWorkflowName(), job.getName(),
            job.getHeadBranch(), OffsetDateTime.now().minusDays(7))
        .stream()
        .reduce((a, b) -> b);
    return recent.map(QueueWaitStat::getRunP50).orElse(null);
  }

  private Integer medianRunDuration(Long repositoryId) {
    // Ordered + bounded by JPA — never loads the whole table.
    List<WorkflowJob> recent = workflowJobRepository
        .findTop50ByRepositoryIdAndStatusAndRunDurationSecondsNotNullOrderByCompletedAtDesc(
            repositoryId, "completed");
    if (recent.isEmpty()) {
      return null;
    }
    int[] durations =
        recent.stream().mapToInt(WorkflowJob::getRunDurationSeconds).sorted().toArray();
    return durations[durations.length / 2];
  }

  /** ETA result. {@code etaSeconds} null for GitHub-hosted; {@code saturation} null otherwise. */
  public record EtaResult(
      Long etaSeconds,
      Integer capacity,
      Integer queueAhead,
      Double saturation,
      Integer referenceQueueP50,
      boolean highDemand) {}
}
