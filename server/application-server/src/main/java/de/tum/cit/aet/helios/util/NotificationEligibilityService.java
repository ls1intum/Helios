package de.tum.cit.aet.helios.util;

import de.tum.cit.aet.helios.notification.NotificationPreference;
import de.tum.cit.aet.helios.notification.NotificationPreferenceRepository;
import de.tum.cit.aet.helios.user.User;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class NotificationEligibilityService {

  /* staging allow‑list */
  @Value("${helios.developers:}")
  private Set<String> heliosDevelopers;

  private final NotificationPreferenceRepository prefRepo;
  private final Environment springEnv;

  /**
   * Method to determine if a user can be notified.
   */
  public boolean canNotify(User user,
                           NotificationPreference.Type type) {

    /* global switch & address */
    if (!user.isNotificationsEnabled() || user.getNotificationEmail() == null) {
      log.info(
          "User {} is not eligible for notifications. Notifications enabled: {}, Email: {}",
          user.getLogin(),
          user.isNotificationsEnabled(),
          user.getNotificationEmail());
      return false;
    }

    /* staging allow‑list */
    if (springEnv.acceptsProfiles(Profiles.of("staging"))
        && !heliosDevelopers.contains(user.getLogin().toLowerCase())) {
      log.info(
          "User {} is not eligible for notifications in staging. Staging allow-list: {}",
          user.getLogin(),
          heliosDevelopers);
      return false;
    }

    /* per‑type preference */
    return prefRepo
        .findByUserAndType(user, type)
        .map(NotificationPreference::isEnabled)
        .orElse(false);
  }
}