package de.tum.cit.aet.helios.workflow.queue.alert;

import de.tum.cit.aet.helios.notification.email.QueueAlertEmailPayload;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertEvent;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertEventRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRule;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRuleRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStat;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStatRepository;
import de.tum.cit.aet.helios.workflow.queue.Runner;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/** Evaluates alert rules every 30s, dedups via open events. See plan §F. */
@Service
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(name = "helios.queue.enabled", havingValue = "true")
public class QueueAlertEvaluator {

  private final QueueAlertRuleRepository ruleRepository;
  private final QueueAlertEventRepository eventRepository;
  private final WorkflowJobRepository workflowJobRepository;
  private final RunnerRepository runnerRepository;
  private final QueueWaitStatRepository statsRepository;
  private final List<AlertChannel> channels;

  @Scheduled(fixedRateString = "${helios.queue.reconcile.alerts.fixedRateMs:30000}")
  @Transactional
  public void evaluate() {
    List<QueueAlertRule> rules = ruleRepository.findByEnabledTrue();
    Map<String, AlertChannel> channelById = new HashMap<>();
    for (AlertChannel c : channels) {
      channelById.put(c.id(), c);
    }
    for (QueueAlertRule rule : rules) {
      try {
        if (inQuietHours(rule)) {
          continue;
        }
        Integer measured = measure(rule);
        boolean fired = measured != null
            && rule.getThresholdSeconds() != null
            && measured > rule.getThresholdSeconds();
        Optional<QueueAlertEvent> open =
            eventRepository.findFirstByRuleIdAndClearedAtIsNull(rule.getId());
        if (fired && open.isEmpty()) {
          openEvent(rule, measured, channelById);
        } else if (!fired && open.isPresent()) {
          closeEvent(open.get());
        }
      } catch (Exception e) {
        log.warn("Alert rule {} evaluation failed: {}", rule.getId(), e.getMessage());
      }
    }
  }

  private boolean inQuietHours(QueueAlertRule rule) {
    if (rule.getQuietHoursCron() == null || rule.getQuietHoursCron().isBlank()) {
      return false;
    }
    try {
      CronExpression expr = CronExpression.parse(rule.getQuietHoursCron());
      LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
      LocalDateTime next = expr.next(now.minusMinutes(1));
      if (next == null) {
        return false;
      }
      return !next.isAfter(now.plusMinutes(1));
    } catch (Exception e) {
      log.warn("Invalid quiet_hours_cron on rule {}: {}", rule.getId(), e.getMessage());
      return false;
    }
  }

  private Integer measure(QueueAlertRule rule) {
    return switch (rule.getKind()) {
      case QUEUE_P95_OVER -> measureQueueP95(rule);
      case RUNNER_OFFLINE_OVER -> measureRunnersOffline(rule);
      case STUCK_JOBS_OVER -> measureStuckJobs(rule);
    };
  }

  private Integer measureQueueP95(QueueAlertRule rule) {
    OffsetDateTime since = OffsetDateTime.now().minusMinutes(rule.getWindowMinutes());
    List<QueueWaitStat> stats = statsRepository.findForWindow(
        rule.getRepositoryId() == null ? 0L : rule.getRepositoryId(), null, null, null, since);
    return stats.stream()
        .map(QueueWaitStat::getQueueP95)
        .filter(java.util.Objects::nonNull)
        .max(Integer::compareTo)
        .orElse(null);
  }

  private Integer measureRunnersOffline(QueueAlertRule rule) {
    return (int) runnerRepository.findByStatus(Runner.Status.OFFLINE).size();
  }

  private Integer measureStuckJobs(QueueAlertRule rule) {
    if (rule.getRepositoryId() != null) {
      return workflowJobRepository.findByRepositoryIdAndStatus(rule.getRepositoryId(), "queued")
          .stream()
          .filter(j -> j.isStuck())
          .toList()
          .size();
    }
    return (int) workflowJobRepository.findAll().stream().filter(j -> j.isStuck()).count();
  }

  private void openEvent(QueueAlertRule rule, int measured, Map<String, AlertChannel> channelById) {
    QueueAlertEvent event = new QueueAlertEvent();
    event.setRuleId(rule.getId());
    event.setRepositoryId(rule.getRepositoryId());
    event.setLabelSetHash(rule.getLabelSetHash());
    event.setMeasuredValue(measured);
    event.setDetails("threshold=" + rule.getThresholdSeconds() + " measured=" + measured);
    eventRepository.save(event);

    QueueAlertEmailPayload payload = new QueueAlertEmailPayload(
        rule.getKind().name(), measured, rule.getThresholdSeconds(), null, event.getDetails());

    if (rule.getChannels() != null) {
      for (String chId : rule.getChannels()) {
        AlertChannel ch = channelById.get(chId);
        if (ch != null) {
          ch.send(payload);
        }
      }
    } else {
      channelById.getOrDefault("EMAIL", channels.isEmpty() ? null : channels.get(0))
          .send(payload);
    }
    log.info("Opened alert event for rule {} measured={}", rule.getId(), measured);
  }

  private void closeEvent(QueueAlertEvent event) {
    event.setClearedAt(OffsetDateTime.now());
    eventRepository.save(event);
    log.info("Cleared alert event {}", event.getId());
  }
}
