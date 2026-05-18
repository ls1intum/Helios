package de.tum.cit.aet.helios.workflow.queue.web;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastWritePermission;
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

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class WorkflowQueueController {

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
      int queued = (int) jobs.stream().filter(j -> "queued".equalsIgnoreCase(j.getStatus())).count();
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
    return ResponseEntity.ok(new QueueDepthDto(labelSets, totalQueued, totalInProgress));
  }

  @GetMapping("/repos/{repoId}/jobs")
  public ResponseEntity<List<QueuedJobDto>> jobs(
      @PathVariable Long repoId,
      @RequestParam(defaultValue = "queued") String status,
      @RequestParam(defaultValue = "100") int limit) {
    List<WorkflowJob> jobs = workflowJobRepository
        .findByRepositoryIdAndStatusInOrderByCreatedAtAsc(repoId, List.of(status))
        .stream().limit(limit).toList();
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
    int samples = stats.stream().mapToInt(s -> s.getSamples() == null ? 0 : s.getSamples()).sum();
    Integer queueP50 = stats.stream().map(QueueWaitStat::getQueueP50)
        .filter(java.util.Objects::nonNull).reduce(Integer::sum).orElse(null);
    Integer queueP90 = stats.stream().map(QueueWaitStat::getQueueP90)
        .filter(java.util.Objects::nonNull).reduce(Integer::sum).orElse(null);
    Integer queueP95 = stats.stream().map(QueueWaitStat::getQueueP95)
        .filter(java.util.Objects::nonNull).reduce(Integer::sum).orElse(null);
    Integer runP50 = stats.stream().map(QueueWaitStat::getRunP50)
        .filter(java.util.Objects::nonNull).reduce(Integer::sum).orElse(null);
    Integer runP90 = stats.stream().map(QueueWaitStat::getRunP90)
        .filter(java.util.Objects::nonNull).reduce(Integer::sum).orElse(null);
    Integer runP95 = stats.stream().map(QueueWaitStat::getRunP95)
        .filter(java.util.Objects::nonNull).reduce(Integer::sum).orElse(null);
    int n = Math.max(1, stats.size());
    if (queueP50 != null) queueP50 /= n;
    if (queueP90 != null) queueP90 /= n;
    if (queueP95 != null) queueP95 /= n;
    if (runP50 != null) runP50 /= n;
    if (runP90 != null) runP90 /= n;
    if (runP95 != null) runP95 /= n;
    List<TrendPoint> trend = stats.stream()
        .map(s -> new TrendPoint(s.getBucketStart(), s.getQueueP50(), s.getRunP50()))
        .toList();
    return ResponseEntity.ok(new QueueStatsDto(samples, queueP50, queueP90, queueP95,
        runP50, runP90, runP95, trend));
  }

  @GetMapping("/org/depth")
  public ResponseEntity<QueueDepthDto> orgDepth() {
    List<WorkflowJob> all = workflowJobRepository.findAll().stream()
        .filter(j -> "queued".equalsIgnoreCase(j.getStatus())
            || "in_progress".equalsIgnoreCase(j.getStatus()))
        .toList();
    Map<String, List<WorkflowJob>> byHash = new LinkedHashMap<>();
    for (WorkflowJob j : all) {
      byHash.computeIfAbsent(j.getLabelSetHash() == null ? "" : j.getLabelSetHash(),
          k -> new ArrayList<>()).add(j);
    }
    int totalQueued = 0;
    int totalInProgress = 0;
    List<LabelSetDepth> labelSets = new ArrayList<>();
    OffsetDateTime now = OffsetDateTime.now();
    for (Map.Entry<String, List<WorkflowJob>> e : byHash.entrySet()) {
      List<WorkflowJob> jobs = e.getValue();
      int queued = (int) jobs.stream().filter(j -> "queued".equalsIgnoreCase(j.getStatus())).count();
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
      labelSets.add(new LabelSetDepth(sample.getLabels(), queued, inProgress, oldestQueuedSeconds,
          sample.getRunnerKind() == null ? null : sample.getRunnerKind().name()));
    }
    return ResponseEntity.ok(new QueueDepthDto(labelSets, totalQueued, totalInProgress));
  }

  // ---- Alert rule CRUD ----

  @GetMapping("/repos/{repoId}/alerts/rules")
  public ResponseEntity<List<AlertRuleDto>> listRules(@PathVariable Long repoId) {
    return ResponseEntity.ok(
        ruleRepository.findByRepositoryId(repoId).stream().map(this::toDto).toList());
  }

  @EnforceAtLeastWritePermission
  @PostMapping("/repos/{repoId}/alerts/rules")
  public ResponseEntity<AlertRuleDto> createRule(
      @PathVariable Long repoId, @Valid @RequestBody AlertRuleDto body) {
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
    return ruleRepository.findById(id).map(rule -> {
      applyDto(rule, body);
      rule.setRepositoryId(repoId);
      return ResponseEntity.ok(toDto(ruleRepository.save(rule)));
    }).orElse(ResponseEntity.notFound().build());
  }

  @EnforceAtLeastWritePermission
  @DeleteMapping("/repos/{repoId}/alerts/rules/{id}")
  public ResponseEntity<Void> deleteRule(@PathVariable Long repoId, @PathVariable Long id) {
    ruleRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/repos/{repoId}/alerts/events")
  public ResponseEntity<List<AlertEventDto>> events(
      @PathVariable Long repoId,
      @RequestParam(defaultValue = "24") int hoursBack) {
    OffsetDateTime since = OffsetDateTime.now().minusHours(hoursBack);
    List<QueueAlertEvent> events = eventRepository.findRecent(repoId, since);
    return ResponseEntity.ok(events.stream().map(this::toDto).toList());
  }

  @EnforceAtLeastWritePermission
  @PostMapping("/admin/backfill")
  public ResponseEntity<String> startBackfill() {
    boolean started = backfillService.start();
    return ResponseEntity.ok(started ? "started" : "already-running");
  }

  private void applyDto(QueueAlertRule rule, AlertRuleDto body) {
    rule.setKind(QueueAlertRule.Kind.valueOf(body.kind()));
    rule.setThresholdSeconds(body.thresholdSeconds());
    rule.setWindowMinutes(body.windowMinutes() == null ? 5 : body.windowMinutes());
    rule.setLabelSetHash(body.labelSetHash());
    rule.setChannels(body.channels() == null ? List.of("EMAIL") : body.channels());
    rule.setEnabled(body.enabled());
    rule.setQuietHoursCron(body.quietHoursCron());
  }

  private AlertRuleDto toDto(QueueAlertRule rule) {
    return new AlertRuleDto(rule.getId(),
        rule.getKind() == null ? null : rule.getKind().name(),
        rule.getThresholdSeconds(), rule.getWindowMinutes(),
        rule.getRepositoryId(), rule.getLabelSetHash(), rule.getChannels(),
        rule.isEnabled(), rule.getQuietHoursCron());
  }

  private AlertEventDto toDto(QueueAlertEvent e) {
    return new AlertEventDto(e.getId(), e.getRuleId(), e.getRepositoryId(),
        e.getFiredAt(), e.getClearedAt(), e.getMeasuredValue(), e.getDetails());
  }
}
