package de.tum.cit.aet.notification.nats;

import io.nats.client.Message;
import io.nats.client.MessageHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class NatsMessageHandler<T> implements MessageHandler {
  protected abstract T parsePayload(byte[] data) throws Exception;

  protected abstract void handleMessage(T payload);

  public abstract String getSubjectPattern();

  public NatsMessageHandler() {}

  /**
   * Determines whether NATS messages should be acknowledged before processing.
   *
   * <p>When set to true, the message will be acknowledged immediately upon receipt, before the
   * handler executes. This is useful for long-running handlers where you want to ensure the message
   * won't be redelivered, even if processing takes a significant amount of time.
   *
   * <p>When false (default), messages are acknowledged only after successful processing.
   *
   * @return boolean indicating if early acknowledgment should be used
   */
  public boolean shouldAcknowledgeEarly() {
    return false;
  }

  @Override
  public void onMessage(Message msg) {
    try {
      T payload = parsePayload(msg.getData());
      handleMessage(payload);
    } catch (Exception e) {
      log.error(
          String.format("Failed to handle NATS message with subject %s", msg.getSubject()), e);
    }
  }
}
