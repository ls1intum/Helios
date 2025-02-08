package de.tum.cit.aet.helios.nats;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.impl.ErrorListenerConsoleImpl;
import io.nats.client.support.Status;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Custom error listener for NATS connection.
 * Inspired by: <a href="https://natsbyexample.com/examples/os/intro/java">natsbyexample</a>
 */
@Log4j2
@Component
public class NatsErrorListener extends ErrorListenerConsoleImpl {

  private final NatsConsumerService natsConsumerService;

  @Autowired
  public NatsErrorListener(@Lazy NatsConsumerService natsConsumerService) {
    this.natsConsumerService = natsConsumerService;
  }


  /**
   * Handles pull status errors for the NATS connection.
   * Logs the error and trys consumer reinitialization if the consumer was deleted.
   */
  @Override
  public void pullStatusError(Connection conn, JetStreamSubscription sub, Status status) {
    String message = this.supplyMessage("[SEVERE] pullStatusError", conn, (Consumer) null, sub,
        new Object[] {"Status: ", status});
    log.error(message);
    // Check if the consumer was deleted
    if (status.getCode() == 409 && "Consumer Deleted".equals(status.getMessage())) {
      log.error("Consumer '{}' was deleted, triggering reinitialization of consumer...",
          sub.getConsumerName());

      // Log error to Sentry
      Sentry.captureMessage(message, SentryLevel.ERROR);
      // Trigger consumer recreation
      natsConsumerService.reinitializeConsumer();
    }
  }

  @Override
  public void heartbeatAlarm(Connection conn, JetStreamSubscription sub, long lastStreamSequence,
                             long lastConsumerSequence) {
    String message = this.supplyMessage("[SEVERE] heartbeatAlarm", conn, (Consumer) null, sub,
        new Object[] {"lastStreamSequence: ", lastStreamSequence, "lastConsumerSequence: ",
            lastConsumerSequence});
    log.error(message);
  }

  @Override
  public void errorOccurred(Connection conn, String error) {
    String message =
        this.supplyMessage("[SEVERE] errorOccurred", conn, (Consumer) null, (Subscription) null,
            new Object[] {"Error: ", error});
    log.error(message);
  }

  @Override
  public void exceptionOccurred(Connection conn, Exception exp) {
    String message =
        this.supplyMessage("[SEVERE] exceptionOccurred", conn, (Consumer) null, (Subscription) null,
            new Object[] {"Exception: ", exp});
    log.error(message);
  }

  @Override
  public void slowConsumerDetected(Connection conn, Consumer consumer) {
    String message =
        this.supplyMessage("[WARN] slowConsumerDetected", conn, consumer, (Subscription) null,
            new Object[0]);
    log.warn(message);
  }

  @Override
  public void messageDiscarded(Connection conn, Message msg) {
    String message =
        this.supplyMessage("[INFO] messageDiscarded", conn, (Consumer) null, (Subscription) null,
            new Object[] {"Message: ", msg});
    log.info(message);
  }

  @Override
  public void unhandledStatus(Connection conn, JetStreamSubscription sub, Status status) {
    String message = this.supplyMessage("[WARN] unhandledStatus", conn, (Consumer) null, sub,
        new Object[] {"Status: ", status});
    log.warn(message);
  }

  @Override
  public void pullStatusWarning(Connection conn, JetStreamSubscription sub, Status status) {
    String message = this.supplyMessage("[WARN] pullStatusWarning", conn, (Consumer) null, sub,
        new Object[] {"Status: ", status});
    log.warn(message);
  }

  @Override
  public void flowControlProcessed(Connection conn, JetStreamSubscription sub, String id,
                                   ErrorListener.FlowControlSource source) {
    String message = this.supplyMessage("[INFO] flowControlProcessed", conn, (Consumer) null, sub,
        new Object[] {"FlowControlSource: ", source});
    log.info(message);
  }

  @Override
  public void socketWriteTimeout(Connection conn) {
    String message = this.supplyMessage("[SEVERE] socketWriteTimeout", conn, (Consumer) null,
        (Subscription) null, new Object[0]);
    log.error(message);
  }
}