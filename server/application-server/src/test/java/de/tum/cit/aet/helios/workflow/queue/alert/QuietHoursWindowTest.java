package de.tum.cit.aet.helios.workflow.queue.alert;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.helios.workflow.queue.QueueAlertEventRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRule;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRuleRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStatRepository;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Replaces the previous cron-as-moment sentinel with active tests against the new HH:mm-HH:mm
 * range semantics (PR #1046 follow-up #6, now fixed). The evaluator's {@code inQuietWindow}
 * helper is package-private so we can test it directly.
 */
@ExtendWith(MockitoExtension.class)
class QuietHoursWindowTest {

  @Mock QueueAlertRuleRepository ruleRepository;
  @Mock QueueAlertEventRepository eventRepository;
  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock RunnerRepository runnerRepository;
  @Mock QueueWaitStatRepository statsRepository;
  @Mock AlertChannel emailChannel;

  private QueueAlertEvaluator newEvaluator() {
    return new QueueAlertEvaluator(
        ruleRepository, eventRepository, workflowJobRepository, runnerRepository,
        statsRepository, List.of(emailChannel));
  }

  private QueueAlertRule withWindow(String window) {
    QueueAlertRule r = new QueueAlertRule();
    r.setId(1L);
    r.setKind(QueueAlertRule.Kind.RUNNER_OFFLINE_OVER);
    r.setThresholdSeconds(0);
    r.setWindowMinutes(5);
    r.setEnabled(true);
    r.setQuietWindow(window);
    return r;
  }

  @Test
  void noQuietWindowMeansAlwaysActive() {
    QueueAlertEvaluator e = newEvaluator();
    assertThat(e.inQuietWindow(withWindow(null), LocalTime.of(3, 0))).isFalse();
    assertThat(e.inQuietWindow(withWindow(""), LocalTime.of(3, 0))).isFalse();
  }

  @Test
  void sameDayWindowSuppressesOnlyInsideRange() {
    QueueAlertEvaluator e = newEvaluator();
    QueueAlertRule r = withWindow("09:00-17:00");
    assertThat(e.inQuietWindow(r, LocalTime.of(8, 59))).isFalse();
    assertThat(e.inQuietWindow(r, LocalTime.of(9, 0))).isTrue();
    assertThat(e.inQuietWindow(r, LocalTime.of(12, 30))).isTrue();
    assertThat(e.inQuietWindow(r, LocalTime.of(17, 0))).isFalse(); // exclusive
    assertThat(e.inQuietWindow(r, LocalTime.of(23, 0))).isFalse();
  }

  @Test
  void overnightWindowSuppressesAcrossMidnight() {
    QueueAlertEvaluator e = newEvaluator();
    QueueAlertRule r = withWindow("22:00-06:00");
    assertThat(e.inQuietWindow(r, LocalTime.of(21, 59))).isFalse();
    assertThat(e.inQuietWindow(r, LocalTime.of(22, 0))).isTrue();
    assertThat(e.inQuietWindow(r, LocalTime.of(23, 59))).isTrue();
    assertThat(e.inQuietWindow(r, LocalTime.of(0, 0))).isTrue();
    assertThat(e.inQuietWindow(r, LocalTime.of(5, 59))).isTrue();
    assertThat(e.inQuietWindow(r, LocalTime.of(6, 0))).isFalse();
    assertThat(e.inQuietWindow(r, LocalTime.of(12, 0))).isFalse();
  }

  @Test
  void invalidWindowDoesNotSuppress() {
    QueueAlertEvaluator e = newEvaluator();
    assertThat(e.inQuietWindow(withWindow("garbage"), LocalTime.of(3, 0))).isFalse();
    assertThat(e.inQuietWindow(withWindow("25:00-26:00"), LocalTime.of(3, 0))).isFalse();
  }
}
