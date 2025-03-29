package de.tum.cit.aet.helios.common.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class NatsPublisher {
  private final Connection natsConnection;
  private final ObjectMapper objectMapper;

  public <T> void publish(String subject, T payload) {
    try {
      byte[] data = objectMapper.writeValueAsBytes(payload);
      natsConnection.publish(subject, data);
      log.debug("Published message to subject: {}", subject);
    } catch (Exception e) {
      log.error("Failed to publish message to subject: " + subject, e);
      throw new NatsPublishException("Failed to publish message", e);
    }
  }
}
