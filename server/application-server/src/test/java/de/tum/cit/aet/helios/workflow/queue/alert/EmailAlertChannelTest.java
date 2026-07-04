package de.tum.cit.aet.helios.workflow.queue.alert;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.nats.NatsNotificationPublisherService;
import de.tum.cit.aet.helios.notification.NotificationPreference;
import de.tum.cit.aet.helios.notification.NotificationPreferenceRepository;
import de.tum.cit.aet.helios.notification.email.QueueAlertEmailPayload;
import de.tum.cit.aet.helios.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailAlertChannelTest {

  @Mock NotificationPreferenceRepository preferenceRepository;
  @Mock NatsNotificationPublisherService publisher;
  @InjectMocks EmailAlertChannel channel;

  private User user(long id) {
    User u = new User();
    u.setId(id);
    return u;
  }

  private QueueAlertEmailPayload p95Payload() {
    return new QueueAlertEmailPayload("QUEUE_P95_OVER", 900, 600, "ls1intum/Helios", "details");
  }

  @Test
  void resolvesRecipientsByNotificationPreferenceTypeAndSendsOnePerUser() {
    when(preferenceRepository.findUsersByTypeEnabled(NotificationPreference.Type.QUEUE_P95_BREACH))
        .thenReturn(List.of(user(1L), user(2L), user(3L)));

    channel.send(p95Payload());

    verify(publisher).send(eq(user(1L)), any(QueueAlertEmailPayload.class));
    verify(publisher).send(eq(user(2L)), any(QueueAlertEmailPayload.class));
    verify(publisher).send(eq(user(3L)), any(QueueAlertEmailPayload.class));
    verify(publisher, times(3)).send(any(User.class), any(QueueAlertEmailPayload.class));
  }

  @Test
  void noRecipientsIsNoop() {
    when(preferenceRepository.findUsersByTypeEnabled(any())).thenReturn(List.of());

    channel.send(p95Payload());

    verify(publisher, never()).send(any(), any());
  }

  @Test
  void singleUserFailureDoesNotBlockOthers() {
    when(preferenceRepository.findUsersByTypeEnabled(NotificationPreference.Type.QUEUE_P95_BREACH))
        .thenReturn(List.of(user(1L), user(2L)));
    doThrow(new RuntimeException("SMTP down"))
        .when(publisher).send(eq(user(1L)), any(QueueAlertEmailPayload.class));

    channel.send(p95Payload()); // must not throw

    verify(publisher).send(eq(user(1L)), any(QueueAlertEmailPayload.class));
    verify(publisher).send(eq(user(2L)), any(QueueAlertEmailPayload.class));
  }

  @Test
  void mapsRuleKindToCorrectNotificationType() {
    when(preferenceRepository.findUsersByTypeEnabled(NotificationPreference.Type.RUNNER_OFFLINE))
        .thenReturn(List.of(user(1L)));
    QueueAlertEmailPayload runnerOffline =
        new QueueAlertEmailPayload("RUNNER_OFFLINE_OVER", 3, 0, null, null);

    channel.send(runnerOffline);

    verify(preferenceRepository).findUsersByTypeEnabled(NotificationPreference.Type.RUNNER_OFFLINE);
    verify(preferenceRepository, never())
        .findUsersByTypeEnabled(NotificationPreference.Type.QUEUE_P95_BREACH);
    verify(publisher).send(eq(user(1L)), any());
  }
}
