package de.tum.cit.aet.helios.workflow.queue.alert;

import de.tum.cit.aet.helios.nats.NatsNotificationPublisherService;
import de.tum.cit.aet.helios.notification.NotificationPreferenceRepository;
import de.tum.cit.aet.helios.notification.email.QueueAlertEmailPayload;
import de.tum.cit.aet.helios.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class EmailAlertChannel implements AlertChannel {

  private final NotificationPreferenceRepository notificationPreferenceRepository;
  private final NatsNotificationPublisherService publisher;

  @Override
  public String id() {
    return "EMAIL";
  }

  @Override
  public void send(QueueAlertEmailPayload payload) {
    List<User> recipients = notificationPreferenceRepository.findUsersByTypeEnabled(payload.type());
    if (recipients.isEmpty()) {
      log.debug("No subscribers for queue alert {}", payload.kind());
      return;
    }
    for (User user : recipients) {
      try {
        publisher.send(user, payload);
      } catch (Exception e) {
        log.warn("Failed to send queue alert email to user {}: {}", user.getId(), e.getMessage());
      }
    }
  }
}
