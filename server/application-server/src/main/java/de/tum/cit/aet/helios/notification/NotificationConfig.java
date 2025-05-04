package de.tum.cit.aet.helios.notification;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configuration class for notification settings.
 *
 * <p>This class reads the notification settings from the application properties and provides a
 * getter method to access the enabled status.
 */
@Configuration
@Log4j2
public class NotificationConfig {
  @Getter
  @Value("${notification.enabled}")
  private boolean enabled;

  /**
   * Logs the notification enabled status when the application is ready.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void log() {
    log.info("Notification enabled: {}", enabled);
  }
}
