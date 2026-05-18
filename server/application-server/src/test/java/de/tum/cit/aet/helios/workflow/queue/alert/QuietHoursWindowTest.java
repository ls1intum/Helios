package de.tum.cit.aet.helios.workflow.queue.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.workflow.queue.QueueAlertEventRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRule;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRuleRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStatRepository;
import de.tum.cit.aet.helios.workflow.queue.Runner;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sentinel for PR #1046 follow-up #6: {@link QueueAlertEvaluator#evaluate()} treats a
 * {@code quiet_hours_cron} as a fire moment, not a duration window. The intent is "suppress
 * alerts overnight 18:00–08:00 weekdays"; the current implementation only suppresses for one
 * minute per night.
 *
 * <p>These tests assert correct windowed behavior — re-enable once the evaluator switches to
 * range semantics.
 */
@ExtendWith(MockitoExtension.class)
class QuietHoursWindowTest {

  @Mock QueueAlertRuleRepository ruleRepository;
  @Mock QueueAlertEventRepository eventRepository;
  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock RunnerRepository runnerRepository;
  @Mock QueueWaitStatRepository statsRepository;
  @Mock AlertChannel emailChannel;

  private QueueAlertRule rule(String quietCron) {
    QueueAlertRule r = new QueueAlertRule();
    r.setId(1L);
    r.setKind(QueueAlertRule.Kind.RUNNER_OFFLINE_OVER);
    r.setThresholdSeconds(0);
    r.setWindowMinutes(5);
    r.setEnabled(true);
    r.setQuietHoursCron(quietCron);
    r.setChannels(List.of("EMAIL"));
    return r;
  }

  private QueueAlertEvaluator newEvaluator() {
    when(emailChannel.id()).thenReturn("EMAIL");
    return new QueueAlertEvaluator(
        ruleRepository, eventRepository, workflowJobRepository, runnerRepository,
        statsRepository, List.of(emailChannel));
  }

  @Test
  @Disabled("PR #1046 follow-up #6: cron-as-moment vs window")
  void quietHoursCronAt3amDoesNotSuppressAtNoon() {
    // A cron firing daily at 3am should NOT suppress an alert evaluated at noon.
    when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule("0 0 3 * * *")));
    Runner offline = new Runner();
    offline.setStatus(Runner.Status.OFFLINE);
    when(runnerRepository.findByStatus(Runner.Status.OFFLINE)).thenReturn(List.of(offline));
    when(eventRepository.findFirstByRuleIdAndClearedAtIsNull(1L)).thenReturn(Optional.empty());

    newEvaluator().evaluate();

    // Real fix should: parse 3am as the START of a quiet window with an explicit end → not in window
    // at noon → alert SHOULD fire.
    verify(emailChannel, times(1)).send(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @Disabled("PR #1046 follow-up #6: cron-as-moment vs window")
  void quietHoursCronCoversFullOvernightWindow() {
    // Intent: suppress 18:00-08:00 weekdays. Whatever the user enters, the evaluator should not
    // fire during the whole interval.
    when(ruleRepository.findByEnabledTrue())
        .thenReturn(List.of(rule("QUIET 18:00-08:00 MON-FRI")));
    Runner offline = new Runner();
    offline.setStatus(Runner.Status.OFFLINE);
    when(runnerRepository.findByStatus(Runner.Status.OFFLINE)).thenReturn(List.of(offline));

    newEvaluator().evaluate();

    // For now the test asserts the desired semantic; the implementation needs a new field schema
    // (start cron + end cron, or LocalTime range) to actually support it.
    verify(emailChannel, never()).send(org.mockito.ArgumentMatchers.any());
  }
}
