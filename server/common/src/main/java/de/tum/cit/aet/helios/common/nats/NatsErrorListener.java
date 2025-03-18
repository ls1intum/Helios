package de.tum.cit.aet.helios.common.nats;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.support.Status;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Custom error listener for NATS connection. Inspired by: <a
 * href="https://natsbyexample.com/examples/os/intro/java">natsbyexample</a>
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class NatsErrorListener implements ErrorListener {

  @Lazy private final BaseNatsConsumerService natsConsumerService;

  /**
   * Handles pull status errors for the NATS connection. Logs the error and trys consumer
   * reinitialization if the consumer was deleted.
   */
  @Override
  public void pullStatusError(Connection conn, JetStreamSubscription sub, Status status) {
    String message =
        formatMessage("[SEVERE] pullStatusError", conn, null, sub, "Status: " + status);
    log.error(message);
    // Check if the consumer was deleted
    if (status.getCode() == 409 && "Consumer Deleted".equals(status.getMessage())) {
      log.error(
          "Consumer '{}' was deleted, triggering reinitialization of consumer...",
          sub.getConsumerName());

      // Log error to Sentry
      Sentry.captureMessage(message, SentryLevel.ERROR);
      // Trigger consumer recreation
      natsConsumerService.reinitializeConsumer();
    }
  }

  @Override
  public void heartbeatAlarm(
      Connection conn,
      JetStreamSubscription sub,
      long lastStreamSequence,
      long lastConsumerSequence) {
    String message =
        formatMessage(
            "[SEVERE] heartbeatAlarm",
            conn,
            null,
            sub,
            String.format(
                "lastStreamSequence: %d, lastConsumerSequence: %d",
                lastStreamSequence, lastConsumerSequence));
    log.error(message);
  }

  @Override
  public void errorOccurred(Connection conn, String error) {
    String message = formatMessage("[SEVERE] errorOccurred", conn, null, null, "Error: " + error);
    log.error(message);
  }

  @Override
  public void exceptionOccurred(Connection conn, Exception exp) {
    String message =
        formatMessage(
            "[SEVERE] exceptionOccurred", conn, null, null, "Exception: " + exp.getMessage());
    log.error(message);
  }

  @Override
  public void slowConsumerDetected(Connection conn, Consumer consumer) {
    String message = formatMessage("[WARN] slowConsumerDetected", conn, consumer, null, null);
    log.warn(message);
  }

  @Override
  public void messageDiscarded(Connection conn, Message msg) {
    String message = formatMessage("[INFO] messageDiscarded", conn, null, null, "Message: " + msg);
    log.info(message);
  }

  @Override
  public void unhandledStatus(Connection conn, JetStreamSubscription sub, Status status) {
    String message = formatMessage("[WARN] unhandledStatus", conn, null, sub, "Status: " + status);
    log.warn(message);
  }

  @Override
  public void pullStatusWarning(Connection conn, JetStreamSubscription sub, Status status) {
    String message =
        formatMessage("[WARN] pullStatusWarning", conn, null, sub, "Status: " + status);
    log.warn(message);
  }

  @Override
  public void flowControlProcessed(
      Connection conn,
      JetStreamSubscription sub,
      String id,
      ErrorListener.FlowControlSource source) {
    String message =
        formatMessage(
            "[INFO] flowControlProcessed", conn, null, sub, "FlowControlSource: " + source);
    log.info(message);
  }

  // @Override
  // public void socketWriteTimeout(Connection conn) {
  //   String message = formatMessage("[SEVERE] socketWriteTimeout", conn, null, null, null);
  //   log.error(message);
  // }

  /** Formats a message with connection, consumer, subscription, and additional details. */
  private String formatMessage(
      String prefix,
      Connection conn,
      Consumer consumer,
      Subscription subscription,
      String additionalInfo) {

    StringBuilder sb = new StringBuilder();
    sb.append(prefix);

    if (conn != null) {
      sb.append(" [Server: ").append(conn.getConnectedUrl()).append("]");
    }

    if (consumer != null) {
      // sb.append(" [Consumer: ").append(consumer.getConsumerName()).append("]");
    }

    if (subscription != null) {
      sb.append(" [Subscription: ").append(subscription.getSubject()).append("]");
    }

    if (additionalInfo != null && !additionalInfo.isEmpty()) {
      sb.append(" - ").append(additionalInfo);
    }

    return sb.toString();
  }
}
