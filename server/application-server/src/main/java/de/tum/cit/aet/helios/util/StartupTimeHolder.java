package de.tum.cit.aet.helios.util;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Holds the moment the Spring Boot application reported it is ready.
 * Inject this bean anywhere you need to know “did this happen after we came online?”
 */
@Component
public class StartupTimeHolder implements ApplicationListener<ApplicationReadyEvent> {

  private final Instant startedAt = Instant.now();
  private Instant readyAt;

  @Override
  public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
    readyAt = Instant.now();
  }

  public Instant getReadyAt() {
    return readyAt == null ? startedAt : readyAt;
  }
}
