package de.tum.cit.aet.helios.status.listeners;

import de.tum.cit.aet.helios.HeliosClient;
import de.tum.cit.aet.helios.status.LifecycleState;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to key Spring-Boot lifecycle events and immediately forwards them
 * to every Helios endpoint.
 *
 * <p>The listener is registered as a <em>regular</em> Spring bean so it is
 * picked up in both servlet and native-image contexts.</p>
 */
@Component
public class BootLifecycleListener {

  private final HeliosClient helios;

  public BootLifecycleListener(HeliosClient helios) {
    this.helios = helios;
  }

  /**
   * ApplicationContext refreshed and {@code @PostConstruct} beans invoked.
   */
  @EventListener
  void onStarted(ApplicationStartedEvent e) {
    helios.push(LifecycleState.STARTING_UP);
  }

  /**
   * Spring Boot failed during startup.
   */
  @EventListener
  void onFailed(ApplicationFailedEvent e) {
    helios.push(LifecycleState.FAILED);
  }

  /**
   * All CommandLineRunners finished → app is ready for traffic.
   */
  @EventListener
  void onReady(ApplicationReadyEvent e) {
    helios.push(LifecycleState.RUNNING);
  }

  /**
   * Context closed gracefully (e.g. SIGTERM / Ctrl-C).
   */
  @EventListener
  void onShutdown(ContextClosedEvent e) {
    helios.push(LifecycleState.SHUTTING_DOWN);
  }
}
