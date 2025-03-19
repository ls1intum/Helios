package de.tum.cit.aet.helios.common.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class JacksonMessageHandler<T> extends NatsMessageHandler<T> {
  @Autowired private ObjectMapper objectMapper;

  protected abstract Class<T> getPayloadClass();

  @Override
  protected T parsePayload(byte[] data) throws Exception {
    return objectMapper.readValue(data, this.getPayloadClass());
  }
}
