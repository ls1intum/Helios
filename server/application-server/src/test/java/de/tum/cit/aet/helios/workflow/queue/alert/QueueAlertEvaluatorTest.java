package de.tum.cit.aet.helios.workflow.queue.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.workflow.queue.QueueAlertEvent;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertEventRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRule;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRuleRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStatRepository;
import de.tum.cit.aet.helios.workflow.queue.Runner;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueAlertEvaluatorTest {

  @Mock QueueAlertRuleRepository ruleRepository;
  @Mock QueueAlertEventRepository eventRepository;
  @Mock WorkflowJobRepository workflowJobRepository;
  @Mock RunnerRepository runnerRepository;
  @Mock QueueWaitStatRepository statsRepository;
  @Mock AlertChannel emailChannel;

  private QueueAlertRule rule(QueueAlertRule.Kind kind, int threshold) {
    QueueAlertRule r = new QueueAlertRule();
    r.setId(1L);
    r.setKind(kind);
    r.setThresholdSeconds(threshold);
    r.setWindowMinutes(5);
    r.setEnabled(true);
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
  void firesAlertWhenThresholdBreachedAndNoOpenEvent() {
    QueueAlertRule r = rule(QueueAlertRule.Kind.RUNNER_OFFLINE_OVER, 0);
    when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));
    Runner offline = new Runner();
    offline.setStatus(Runner.Status.OFFLINE);
    when(runnerRepository.findByStatus(Runner.Status.OFFLINE)).thenReturn(List.of(offline));
    when(eventRepository.findFirstByRuleIdAndClearedAtIsNull(1L)).thenReturn(Optional.empty());

    newEvaluator().evaluate();

    ArgumentCaptor<QueueAlertEvent> captor = ArgumentCaptor.forClass(QueueAlertEvent.class);
    verify(eventRepository).save(captor.capture());
    verify(emailChannel).send(any());
    assertThat(captor.getValue().getRuleId()).isEqualTo(1L);
    assertThat(captor.getValue().getMeasuredValue()).isEqualTo(1);
  }

  @Test
  void doesNotFireAgainIfEventAlreadyOpen() {
    QueueAlertRule r = rule(QueueAlertRule.Kind.RUNNER_OFFLINE_OVER, 0);
    when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));
    Runner offline = new Runner();
    offline.setStatus(Runner.Status.OFFLINE);
    when(runnerRepository.findByStatus(Runner.Status.OFFLINE)).thenReturn(List.of(offline));
    QueueAlertEvent open = new QueueAlertEvent();
    open.setId(99L);
    open.setRuleId(1L);
    when(eventRepository.findFirstByRuleIdAndClearedAtIsNull(1L)).thenReturn(Optional.of(open));

    newEvaluator().evaluate();

    verify(emailChannel, times(0)).send(any());
    // No save() for a new open event.
    verify(eventRepository, times(0)).save(any(QueueAlertEvent.class));
  }

  @Test
  void clearsOpenEventWhenMeasurementBackBelowThreshold() {
    QueueAlertRule r = rule(QueueAlertRule.Kind.RUNNER_OFFLINE_OVER, 5);
    when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));
    when(runnerRepository.findByStatus(Runner.Status.OFFLINE)).thenReturn(List.of());
    QueueAlertEvent open = new QueueAlertEvent();
    open.setId(99L);
    open.setRuleId(1L);
    when(eventRepository.findFirstByRuleIdAndClearedAtIsNull(1L)).thenReturn(Optional.of(open));

    newEvaluator().evaluate();

    ArgumentCaptor<QueueAlertEvent> captor = ArgumentCaptor.forClass(QueueAlertEvent.class);
    verify(eventRepository).save(captor.capture());
    assertThat(captor.getValue().getClearedAt()).isNotNull();
  }

  @Test
  void disabledRuleIsSkipped() {
    when(ruleRepository.findByEnabledTrue()).thenReturn(List.of());

    newEvaluator().evaluate();

    verify(emailChannel, times(0)).send(any());
  }

  @Test
  void quietHoursCronSkipsEvaluationDuringMatchingMinute() {
    QueueAlertRule r = rule(QueueAlertRule.Kind.RUNNER_OFFLINE_OVER, 0);
    // Cron firing every minute → "next" from a minute ago should fall inside the window.
    r.setQuietHoursCron("0 * * * * *");
    when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

    newEvaluator().evaluate();

    // No event saved, no email sent — quiet path.
    verify(eventRepository, times(0)).save(any(QueueAlertEvent.class));
    verify(emailChannel, times(0)).send(any());
  }
}
