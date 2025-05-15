package de.tum.cit.aet.helios.status.listeners;

import de.tum.cit.aet.helios.HeliosClient;
import de.tum.cit.aet.helios.HeliosStatusProperties;
import de.tum.cit.aet.helios.status.LifecycleState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatScheduler {

  private final HeliosClient helios;
  private final HeliosStatusProperties props;

  HeartbeatScheduler(HeliosClient helios, HeliosStatusProperties props) {
    this.helios = helios;
    this.props = props;
  }

  /**
   * Runs every {@code props.heartbeatInterval} milliseconds.
   * Only sends if {@code props.enabled()} is true.
   */
  @Scheduled(fixedDelayString =
      "#{@'de.tum.cit.aet.helios.HeliosStatusProperties'.heartbeatInterval.toMillis()}")
  void sendHeartbeat() {
    if (props.enabled()) {
      helios.push(LifecycleState.RUNNING).subscribe();
    }
  }
}
