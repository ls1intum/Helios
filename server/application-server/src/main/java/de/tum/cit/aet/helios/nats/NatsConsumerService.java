package de.tum.cit.aet.helios.nats;

import de.tum.cit.aet.helios.common.nats.BaseNatsConsumerService;
import de.tum.cit.aet.helios.common.nats.NatsErrorListener;
import de.tum.cit.aet.helios.common.nats.NatsMessageHandlerRegistry;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Order(value = 1)
@Service
@Log4j2
public class NatsConsumerService extends BaseNatsConsumerService {
  public NatsConsumerService(
      Environment environment,
      NatsMessageHandlerRegistry handlerRegistry,
      @Lazy NatsErrorListener natsErrorListener) {
    super(environment, handlerRegistry, natsErrorListener);
  }

  @Override
  protected String getStreamName() {
    return "github";
  }
}
