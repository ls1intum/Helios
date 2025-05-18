package de.tum.cit.aet.helios.status.listeners;

import de.tum.cit.aet.helios.HeliosClient;
import de.tum.cit.aet.helios.HeliosStatusProperties;
import de.tum.cit.aet.helios.status.LifecycleState;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Configures a programmatic fixed-delay task that sends a <em>heartbeat</em>
 * every {@code helios.status.heartbeat-interval} seconds.
 *
 * <p>The implementation sticks to {@link SchedulingConfigurer} so we do not
 * accidentally create a second TaskScheduler bean.</p>
 */
@Component
public class HeartbeatScheduler implements SchedulingConfigurer {

  private final HeliosClient helios;
  private final HeliosStatusProperties props;

  public HeartbeatScheduler(HeliosClient helios, HeliosStatusProperties props) {
    this.helios = helios;
    this.props = props;
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    TaskScheduler scheduler = taskRegistrar.getScheduler();
    if (scheduler != null && props.enabled()) {
      Runnable heartbeatTask = () -> helios.push(LifecycleState.RUNNING);
      scheduler.scheduleWithFixedDelay(heartbeatTask, props.heartbeatInterval());
    }
  }
}
