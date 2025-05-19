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
 * Observes key Spring Boot application lifecycle events and pushes corresponding
 * status updates to all configured Helios endpoints.
 *
 * <p>Triggered on application startup, readiness, failure, and shutdown.</p>
 */
@Component
public class BootLifecycleListener {

  private final HeliosClient helios;

  /**
   * Constructs the listener with the given Helios client.
   *
   * @param helios the client used to push lifecycle updates
   */
  public BootLifecycleListener(HeliosClient helios) {
    this.helios = helios;
  }

  /**
   * Invoked when the application starts (context refreshed, PostConstruct run).
   */
  @EventListener
  void onStarted(ApplicationStartedEvent e) {
    helios.pushStatusUpdate(LifecycleState.STARTING_UP);
  }

  /**
   * Invoked when the application fails to start.
   */
  @EventListener
  void onFailed(ApplicationFailedEvent e) {
    helios.pushStatusUpdate(LifecycleState.FAILED);
  }

  /**
   * Invoked when the app is fully initialized and ready to serve traffic.
   */
  @EventListener
  void onReady(ApplicationReadyEvent e) {
    helios.pushStatusUpdate(LifecycleState.RUNNING);
  }

  /**
   * Invoked on graceful shutdown (e.g., SIGTERM).
   */
  @EventListener
  void onShutdown(ContextClosedEvent e) {
    helios.pushStatusUpdate(LifecycleState.SHUTTING_DOWN);
  }
}
