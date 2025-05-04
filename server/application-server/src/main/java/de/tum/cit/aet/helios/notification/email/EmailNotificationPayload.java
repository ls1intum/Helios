package de.tum.cit.aet.helios.notification.email;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.cit.aet.helios.notification.NotificationPreference;

/**
 * Marker for every outbound e‑mail notification payload.
 * Every implementation just needs to say what template it belongs to.
 */
public interface EmailNotificationPayload {

  /**
   * The FreeMarker/Thymeleaf template in *kebab‑case*, e.g. {@code deployment-failure}.
   */
  @JsonIgnore
  String template();

  /**
   * Mail subject line.
   */
  @JsonIgnore
  String subject();

  /** Which preference row controls this mail. */
  @JsonIgnore NotificationPreference.Type type();
}

