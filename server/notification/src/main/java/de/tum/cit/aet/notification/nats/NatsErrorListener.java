package de.tum.cit.aet.notification.nats;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.impl.ErrorListenerConsoleImpl;
import io.nats.client.support.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Custom error listener for NATS connection. */
@Slf4j
@Component
public class NatsErrorListener extends ErrorListenerConsoleImpl {

  private NatsNotificationConsumerService natsConsumerService;

  @Autowired
  @Lazy
  public void setNatsConsumerService(NatsNotificationConsumerService natsConsumerService) {
    this.natsConsumerService = natsConsumerService;
  }

  /**
   * Handles pull status errors for the NATS connection. Logs the error and tries consumer
   * reinitialization if the consumer was deleted.
   */
  @Override
  public void pullStatusError(Connection conn, JetStreamSubscription sub, Status status) {
    String message =
        this.supplyMessage(
            "[SEVERE] pullStatusError",
            conn,
            (Consumer) null,
            sub,
            new Object[] {"Status: ", status});
    log.error(message);
    // Check if the consumer was deleted
    if (status.getCode() == 409 && "Consumer Deleted".equals(status.getMessage())) {
      log.error(
          "Consumer '{}' was deleted, triggering reinitialization of consumer...",
          sub.getConsumerName());

      // Trigger consumer recreation
      if (natsConsumerService != null) {
        natsConsumerService.reinitializeConsumer();
      }
    }
  }

  @Override
  public void heartbeatAlarm(
      Connection conn,
      JetStreamSubscription sub,
      long lastStreamSequence,
      long lastConsumerSequence) {
    String message =
        this.supplyMessage(
            "[SEVERE] heartbeatAlarm",
            conn,
            (Consumer) null,
            sub,
            new Object[] {
              "lastStreamSequence: ",
              lastStreamSequence,
              "lastConsumerSequence: ",
              lastConsumerSequence
            });
    log.error(message);
  }

  @Override
  public void errorOccurred(Connection conn, String error) {
    String message =
        this.supplyMessage(
            "[SEVERE] errorOccurred",
            conn,
            (Consumer) null,
            (Subscription) null,
            new Object[] {"Error: ", error});
    log.error(message);
  }

  @Override
  public void exceptionOccurred(Connection conn, Exception exp) {
    String message =
        this.supplyMessage(
            "[SEVERE] exceptionOccurred",
            conn,
            (Consumer) null,
            (Subscription) null,
            new Object[] {"Exception: ", exp});
    log.error(message);
  }

  @Override
  public void slowConsumerDetected(Connection conn, Consumer consumer) {
    String message =
        this.supplyMessage(
            "[WARN] slowConsumerDetected", conn, consumer, (Subscription) null, new Object[0]);
    log.warn(message);
  }

  @Override
  public void messageDiscarded(Connection conn, Message msg) {
    String message =
        this.supplyMessage(
            "[INFO] messageDiscarded",
            conn,
            (Consumer) null,
            (Subscription) null,
            new Object[] {"Message: ", msg});
    log.info(message);
  }

  @Override
  public void unhandledStatus(Connection conn, JetStreamSubscription sub, Status status) {
    String message =
        this.supplyMessage(
            "[WARN] unhandledStatus",
            conn,
            (Consumer) null,
            sub,
            new Object[] {"Status: ", status});
    log.warn(message);
  }

  @Override
  public void pullStatusWarning(Connection conn, JetStreamSubscription sub, Status status) {
    String message =
        this.supplyMessage(
            "[WARN] pullStatusWarning",
            conn,
            (Consumer) null,
            sub,
            new Object[] {"Status: ", status});
    log.warn(message);
  }

  @Override
  public void flowControlProcessed(
      Connection conn,
      JetStreamSubscription sub,
      String id,
      ErrorListener.FlowControlSource source) {
    String message =
        this.supplyMessage(
            "[INFO] flowControlProcessed",
            conn,
            (Consumer) null,
            sub,
            new Object[] {"FlowControlSource: ", source});
    log.info(message);
  }

  @Override
  public void socketWriteTimeout(Connection conn) {
    String message =
        this.supplyMessage(
            "[SEVERE] socketWriteTimeout",
            conn,
            (Consumer) null,
            (Subscription) null,
            new Object[0]);
    log.error(message);
  }
}
