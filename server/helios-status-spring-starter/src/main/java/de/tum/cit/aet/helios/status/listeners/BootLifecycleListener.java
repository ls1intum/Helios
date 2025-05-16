package de.tum.cit.aet.helios.status.listeners;

import de.tum.cit.aet.helios.HeliosClient;
import de.tum.cit.aet.helios.status.LifecycleState;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BootLifecycleListener {

  private final HeliosClient helios;

  BootLifecycleListener(HeliosClient helios) {
    this.helios = helios;
  }

  @EventListener
  void onStarted(ApplicationStartedEvent e) {
    helios.push(LifecycleState.STARTING_UP);
  }

  @EventListener
  void onReady(ApplicationReadyEvent e) {
    helios.push(LifecycleState.RUNNING);
  }

  @EventListener
  void onFailed(ApplicationFailedEvent e) {
    helios.push(LifecycleState.FAILED);
  }

  @EventListener
  void onShutdown(ContextClosedEvent e) {
    helios.push(LifecycleState.SHUTTING_DOWN);
  }
}
