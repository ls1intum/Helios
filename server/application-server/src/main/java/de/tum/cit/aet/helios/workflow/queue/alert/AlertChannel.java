package de.tum.cit.aet.helios.workflow.queue.alert;

import de.tum.cit.aet.helios.notification.email.QueueAlertEmailPayload;

/**
 * Strategy for delivering a queue alert event. Plan §F — phase-1 implementation is email only;
 * Slack/webhook channels slot in later behind this interface.
 */
public interface AlertChannel {

  /** Channel id matching {@code queue_alert_rule.channels}. */
  String id();

  /** Sends the alert to all users subscribed to the rule's notification type. */
  void send(QueueAlertEmailPayload payload);
}
