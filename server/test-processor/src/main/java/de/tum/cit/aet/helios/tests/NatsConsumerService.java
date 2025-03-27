package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.common.nats.BaseNatsConsumerService;
import de.tum.cit.aet.helios.common.nats.NatsErrorListener;
import de.tum.cit.aet.helios.common.nats.NatsMessageHandlerRegistry;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Order(value = 1)
@Service
@Log4j2
public class NatsConsumerService extends BaseNatsConsumerService {
  public NatsConsumerService(
      NatsMessageHandlerRegistry handlerRegistry, @Lazy NatsErrorListener natsErrorListener) {
    super(handlerRegistry, natsErrorListener);
  }

  @Override
  protected String getStreamName() {
    return "github";
  }
}
