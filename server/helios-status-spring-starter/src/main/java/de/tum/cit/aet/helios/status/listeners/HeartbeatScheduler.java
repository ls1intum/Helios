package de.tum.cit.aet.helios.status.listeners;

import de.tum.cit.aet.helios.HeliosClient;
import de.tum.cit.aet.helios.HeliosStatusProperties;
import de.tum.cit.aet.helios.status.LifecycleState;
import java.util.HashMap;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Configures and registers a periodic heartbeat task that pushes the {@code RUNNING}
 * state at fixed intervals to Helios.
 *
 * <p>This scheduler is activated only if {@code helios.status.enabled=true}. It avoids
 * defining a new {@link TaskScheduler} bean and
 * integrates directly via {@link SchedulingConfigurer}.</p>
 */
@Component
public class HeartbeatScheduler implements SchedulingConfigurer {

  private final HeliosClient helios;
  private final HeliosStatusProperties props;

  /**
   * Constructs the heartbeat scheduler with a shared Helios client and configuration.
   *
   * @param helios the shared Helios client instance
   * @param props the Helios status properties
   */
  public HeartbeatScheduler(HeliosClient helios, HeliosStatusProperties props) {
    this.helios = helios;
    this.props = props;
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    TaskScheduler scheduler = taskRegistrar.getScheduler();
    if (scheduler != null && props.enabled()) {
      Runnable heartbeatTask = () -> helios.pushStatusUpdate(LifecycleState.RUNNING);
      scheduler.scheduleWithFixedDelay(heartbeatTask, props.heartbeatInterval());
    }
  }
}
