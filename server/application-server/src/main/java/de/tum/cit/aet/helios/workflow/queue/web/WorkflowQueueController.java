package de.tum.cit.aet.helios.workflow.queue.web;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAdmin;
import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastWritePermission;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertEvent;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertEventRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRule;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRuleRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueEtaService;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStat;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStatRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJob;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import de.tum.cit.aet.helios.workflow.queue.reconcile.WorkflowJobBackfillService;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.AlertEventDto;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.AlertRuleDto;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.LabelSetDepth;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.QueueDepthDto;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.QueueStatsDto;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.QueuedJobDto;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.TrendPoint;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "helios.queue.enabled", havingValue = "true")
public class WorkflowQueueController {

  private static final int MAX_JOBS_LIMIT = 500;

  private final WorkflowJobRepository workflowJobRepository;
  private final QueueWaitStatRepository statsRepository;
  private final QueueAlertRuleRepository ruleRepository;
  private final QueueAlertEventRepository eventRepository;
  private final QueueEtaService etaService;
  private final WorkflowJobBackfillService backfillService;

  @GetMapping("/repos/{repoId}/depth")
  public ResponseEntity<QueueDepthDto> depth(@PathVariable Long repoId) {
    List<WorkflowJob> active = workflowJobRepository
        .findByRepositoryIdAndStatusInOrderByCreatedAtAsc(repoId, List.of("queued", "in_progress"));
    return ResponseEntity.ok(aggregateDepth(active));
  }

  @GetMapping("/repos/{repoId}/jobs")
  public ResponseEntity<List<QueuedJobDto>> jobs(
      @PathVariable Long repoId,
      @RequestParam(defaultValue = "queued") String status,
      @RequestParam(defaultValue = "100") int limit) {
    int safeLimit = Math.max(1, Math.min(limit, MAX_JOBS_LIMIT));
    List<WorkflowJob> jobs = workflowJobRepository
        .findByRepositoryIdAndStatusInOrderByCreatedAtAsc(
            repoId, List.of(status), PageRequest.of(0, safeLimit));
    OffsetDateTime now = OffsetDateTime.now();
    Map<String, Integer> positionByHash = new HashMap<>();
    List<QueuedJobDto> out = new ArrayList<>();
    for (WorkflowJob j : jobs) {
      int pos = positionByHash.merge(
          j.getLabelSetHash() == null ? "" : j.getLabelSetHash(), 1, Integer::sum);
      Long waitSeconds = j.getCreatedAt() == null
          ? null : Duration.between(j.getCreatedAt(), now).getSeconds();
      Long eta = etaService.computeEta(j).etaSeconds();
      out.add(new QueuedJobDto(
          j.getId(),
          j.getWorkflowRunId(),
          j.getWorkflowName(),
          j.getName(),
          j.getHeadBranch(),
          j.getLabels(),
          waitSeconds == null ? null : waitSeconds.intValue(),
          eta,
          pos,
          j.getQueuedReason() == null ? null : j.getQueuedReason().name(),
          j.isStuck(),
          j.getRunnerKind() == null ? null : j.getRunnerKind().name()));
    }
    return ResponseEntity.ok(out);
  }

  @GetMapping("/repos/{repoId}/stats")
  public ResponseEntity<QueueStatsDto> stats(
      @PathVariable Long repoId,
      @RequestParam(required = false) String workflow,
      @RequestParam(required = false) String job,
      @RequestParam(required = false) String branch,
      @RequestParam(defaultValue = "7d") String window) {
    int days = "30d".equalsIgnoreCase(window) ? 30 : 7;
    OffsetDateTime since = OffsetDateTime.now().minusDays(days);
    List<QueueWaitStat> stats = statsRepository.findForWindow(repoId, workflow, job, branch, since);
    int totalSamples =
        stats.stream().mapToInt(s -> s.getSamples() == null ? 0 : s.getSamples()).sum();

    // Sample-weighted percentile estimate: weight each bucket's per-percentile value by its
    // sample count. This is a closer approximation to a true window percentile than the unweighted
    // mean used previously, while staying O(buckets). See PR #1046 follow-up #7.
    Integer queueP50 = weightedPercentile(stats, QueueWaitStat::getQueueP50);
    Integer queueP90 = weightedPercentile(stats, QueueWaitStat::getQueueP90);
    Integer queueP95 = weightedPercentile(stats, QueueWaitStat::getQueueP95);
    Integer runP50 = weightedPercentile(stats, QueueWaitStat::getRunP50);
    Integer runP90 = weightedPercentile(stats, QueueWaitStat::getRunP90);
    Integer runP95 = weightedPercentile(stats, QueueWaitStat::getRunP95);

    List<TrendPoint> trend = stats.stream()
        .map(s -> new TrendPoint(s.getBucketStart(), s.getQueueP50(), s.getRunP50()))
        .toList();
    return ResponseEntity.ok(new QueueStatsDto(totalSamples, queueP50, queueP90, queueP95,
        runP50, runP90, runP95, trend));
  }

  @GetMapping("/org/depth")
  public ResponseEntity<QueueDepthDto> orgDepth() {
    // SQL-constrained — does not load every historical workflow_job row.
    List<WorkflowJob> active = workflowJobRepository
        .findByStatusInOrderByCreatedAtAsc(List.of("queued", "in_progress"));
    return ResponseEntity.ok(aggregateDepth(active));
  }

  // ---- Alert rule CRUD ----

  @GetMapping("/repos/{repoId}/alerts/rules")
  public ResponseEntity<List<AlertRuleDto>> listRules(@PathVariable Long repoId) {
    return ResponseEntity.ok(
        ruleRepository.findByRepositoryId(repoId).stream().map(this::toDto).toList());
  }

  /**
   * {@code @EnforceAtLeastWritePermission} grants the WRITE role for the repo in the
   * X-Repository-Id header (the request's {@link RepositoryContext}), not for the {@code repoId}
   * path variable. Guard that they are the same repo, so a user with write access to repo A cannot
   * create/edit/delete alert rules on repo B by keeping A in the header while putting B in the path.
   */
  private void assertRepoInContext(Long repoId) {
    Long contextRepoId = RepositoryContext.getRepositoryId();
    if (contextRepoId == null || !contextRepoId.equals(repoId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Path repository does not match the authorized repository.");
    }
  }

  @EnforceAtLeastWritePermission
  @PostMapping("/repos/{repoId}/alerts/rules")
  public ResponseEntity<AlertRuleDto> createRule(
      @PathVariable Long repoId, @Valid @RequestBody AlertRuleDto body) {
    assertRepoInContext(repoId);
    QueueAlertRule rule = new QueueAlertRule();
    applyDto(rule, body);
    rule.setRepositoryId(repoId);
    QueueAlertRule saved = ruleRepository.save(rule);
    return ResponseEntity.ok(toDto(saved));
  }

  @EnforceAtLeastWritePermission
  @PutMapping("/repos/{repoId}/alerts/rules/{id}")
  public ResponseEntity<AlertRuleDto> updateRule(
      @PathVariable Long repoId,
      @PathVariable Long id,
      @Valid @RequestBody AlertRuleDto body) {
    assertRepoInContext(repoId);
    return ruleRepository.findByIdAndRepositoryId(id, repoId).map(rule -> {
      applyDto(rule, body);
      rule.setRepositoryId(repoId);
      return ResponseEntity.ok(toDto(ruleRepository.save(rule)));
    }).orElse(ResponseEntity.notFound().build());
  }

  @EnforceAtLeastWritePermission
  @DeleteMapping("/repos/{repoId}/alerts/rules/{id}")
  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<Void> deleteRule(@PathVariable Long repoId, @PathVariable Long id) {
    assertRepoInContext(repoId);
    long deleted = ruleRepository.deleteByIdAndRepositoryId(id, repoId);
    return deleted > 0 ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }

  @GetMapping("/repos/{repoId}/alerts/events")
  public ResponseEntity<List<AlertEventDto>> events(
      @PathVariable Long repoId,
      @RequestParam(defaultValue = "24") int hoursBack) {
    OffsetDateTime since = OffsetDateTime.now().minusHours(hoursBack);
    List<QueueAlertEvent> events = eventRepository.findRecent(repoId, since);
    return ResponseEntity.ok(events.stream().map(this::toDto).toList());
  }

  /** Admin-only — 30-day backfill consumes a meaningful GitHub rate-limit slice. */
  @EnforceAdmin
  @PostMapping("/admin/backfill")
  public ResponseEntity<String> startBackfill() {
    boolean started = backfillService.start();
    return ResponseEntity.ok(started ? "started" : "already-running");
  }

  // ---- helpers ----

  private QueueDepthDto aggregateDepth(List<WorkflowJob> active) {
    Map<String, List<WorkflowJob>> byHash = new LinkedHashMap<>();
    for (WorkflowJob j : active) {
      byHash.computeIfAbsent(j.getLabelSetHash() == null ? "" : j.getLabelSetHash(),
          k -> new ArrayList<>()).add(j);
    }
    List<LabelSetDepth> labelSets = new ArrayList<>();
    int totalQueued = 0;
    int totalInProgress = 0;
    OffsetDateTime now = OffsetDateTime.now();
    for (Map.Entry<String, List<WorkflowJob>> e : byHash.entrySet()) {
      List<WorkflowJob> jobs = e.getValue();
      int queued =
          (int) jobs.stream().filter(j -> "queued".equalsIgnoreCase(j.getStatus())).count();
      int inProgress =
          (int) jobs.stream().filter(j -> "in_progress".equalsIgnoreCase(j.getStatus())).count();
      totalQueued += queued;
      totalInProgress += inProgress;
      Long oldestQueuedSeconds = jobs.stream()
          .filter(j -> "queued".equalsIgnoreCase(j.getStatus()) && j.getCreatedAt() != null)
          .map(j -> Duration.between(j.getCreatedAt(), now).getSeconds())
          .max(Long::compareTo)
          .orElse(null);
      WorkflowJob sample = jobs.get(0);
      labelSets.add(new LabelSetDepth(
          sample.getLabels(),
          queued,
          inProgress,
          oldestQueuedSeconds,
          sample.getRunnerKind() == null ? null : sample.getRunnerKind().name()));
    }
    return new QueueDepthDto(labelSets, totalQueued, totalInProgress);
  }

  private Integer weightedPercentile(
      List<QueueWaitStat> stats,
      java.util.function.Function<QueueWaitStat, Integer> field) {
    long totalSamples = 0L;
    long weighted = 0L;
    for (QueueWaitStat s : stats) {
      Integer v = field.apply(s);
      if (v == null || s.getSamples() == null) {
        continue;
      }
      totalSamples += s.getSamples();
      weighted += (long) v * s.getSamples();
    }
    return totalSamples == 0 ? null : (int) (weighted / totalSamples);
  }

  private void applyDto(QueueAlertRule rule, AlertRuleDto body) {
    rule.setKind(QueueAlertRule.Kind.valueOf(body.kind()));
    rule.setThresholdSeconds(body.thresholdSeconds());
    rule.setWindowMinutes(body.windowMinutes() == null ? 5 : body.windowMinutes());
    rule.setLabelSetHash(body.labelSetHash());
    rule.setChannels(body.channels() == null ? List.of("EMAIL") : body.channels());
    rule.setEnabled(body.enabled());
    rule.setQuietWindow(body.quietWindow());
  }

  private AlertRuleDto toDto(QueueAlertRule rule) {
    return new AlertRuleDto(rule.getId(),
        rule.getKind() == null ? null : rule.getKind().name(),
        rule.getThresholdSeconds(), rule.getWindowMinutes(),
        rule.getRepositoryId(), rule.getLabelSetHash(), rule.getChannels(),
        rule.isEnabled(), rule.getQuietWindow());
  }

  private AlertEventDto toDto(QueueAlertEvent e) {
    return new AlertEventDto(e.getId(), e.getRuleId(), e.getRepositoryId(),
        e.getFiredAt(), e.getClearedAt(), e.getMeasuredValue(), e.getDetails());
  }
}
