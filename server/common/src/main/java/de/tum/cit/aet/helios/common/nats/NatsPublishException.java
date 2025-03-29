package de.tum.cit.aet.helios.common.nats;

public class NatsPublishException extends RuntimeException {
  public NatsPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
