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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Evaluates alert rules every 30s, dedups via open events. See plan §F.
 *
 * <p>Quiet windows are encoded as {@code HH:mm-HH:mm} ranges (local time). End-before-start spans
 * midnight (e.g. {@code 22:00-06:00}).
 */
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
        if (inQuietWindow(rule, LocalTime.now())) {
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

  /**
   * Returns true if {@code now} is inside the rule's quiet window. Package-private for tests.
   * Window is {@code HH:mm-HH:mm} local time; end-before-start crosses midnight.
   */
  boolean inQuietWindow(QueueAlertRule rule, LocalTime now) {
    String window = rule.getQuietWindow();
    if (window == null || window.isBlank()) {
      return false;
    }
    String[] parts = window.split("-");
    if (parts.length != 2) {
      log.warn("Invalid quiet_window on rule {}: {}", rule.getId(), window);
      return false;
    }
    LocalTime start;
    LocalTime end;
    try {
      start = LocalTime.parse(parts[0].trim());
      end = LocalTime.parse(parts[1].trim());
    } catch (Exception e) {
      log.warn("Invalid quiet_window times on rule {}: {}", rule.getId(), window);
      return false;
    }
    if (start.equals(end)) {
      return false; // Empty window.
    }
    if (start.isBefore(end)) {
      // Same-day window: [start, end).
      return !now.isBefore(start) && now.isBefore(end);
    }
    // Overnight: [start, 24:00) ∪ [00:00, end).
    return !now.isBefore(start) || now.isBefore(end);
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
    // repositoryId NULL ⇒ org-wide; label_set_hash NULL ⇒ any label set.
    List<QueueWaitStat> stats =
        statsRepository.findForRuleWindow(rule.getRepositoryId(), rule.getLabelSetHash(), since);
    return stats.stream()
        .map(QueueWaitStat::getQueueP95)
        .filter(Objects::nonNull)
        .max(Integer::compareTo)
        .orElse(null);
  }

  private Integer measureRunnersOffline(QueueAlertRule rule) {
    // Offline runners. If the rule scopes to a label-set, only count runners whose label set
    // matches; otherwise count all offline runners.
    List<Runner> offline = runnerRepository.findByStatus(Runner.Status.OFFLINE);
    if (rule.getLabelSetHash() == null) {
      return offline.size();
    }
    return (int) offline.stream()
        .filter(r -> rule.getLabelSetHash().equals(hashOrEmpty(r.getLabels())))
        .count();
  }

  private Integer measureStuckJobs(QueueAlertRule rule) {
    return (int) workflowJobRepository.countCurrentlyStuck(
        rule.getRepositoryId(), rule.getLabelSetHash());
  }

  private String hashOrEmpty(List<String> labels) {
    return labels == null
        ? ""
        : de.tum.cit.aet.helios.workflow.queue.LabelSets.hash(labels);
  }

  private void openEvent(QueueAlertRule rule, int measured, Map<String, AlertChannel> channelById) {
    QueueAlertEvent event = new QueueAlertEvent();
    event.setRuleId(rule.getId());
    event.setRepositoryId(rule.getRepositoryId());
    event.setLabelSetHash(rule.getLabelSetHash());
    event.setMeasuredValue(measured);
    event.setDetails("threshold=" + rule.getThresholdSeconds() + " measured=" + measured
        + " unit=" + rule.getKind().unit());
    eventRepository.save(event);

    QueueAlertEmailPayload payload = new QueueAlertEmailPayload(
        rule.getKind().name(), measured, rule.getThresholdSeconds(), null, event.getDetails());

    if (rule.getChannels() != null && !rule.getChannels().isEmpty()) {
      for (String chId : rule.getChannels()) {
        AlertChannel ch = channelById.get(chId);
        if (ch != null) {
          ch.send(payload);
        } else {
          log.warn("Unknown alert channel {} on rule {}", chId, rule.getId());
        }
      }
    } else {
      AlertChannel email = channelById.get("EMAIL");
      if (email != null) {
        email.send(payload);
      }
    }
    log.info("Opened alert event for rule {} measured={} unit={}",
        rule.getId(), measured, rule.getKind().unit());
  }

  private void closeEvent(QueueAlertEvent event) {
    event.setClearedAt(OffsetDateTime.now());
    eventRepository.save(event);
    log.info("Cleared alert event {}", event.getId());
  }
}
