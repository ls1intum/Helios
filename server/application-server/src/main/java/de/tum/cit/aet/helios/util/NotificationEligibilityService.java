package de.tum.cit.aet.helios.util;

import de.tum.cit.aet.helios.notification.NotificationPreference;
import de.tum.cit.aet.helios.notification.NotificationPreferenceRepository;
import de.tum.cit.aet.helios.user.User;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class NotificationEligibilityService {

  /* staging allow‑list */
  private static final Set<String> STAGING_ALLOWLIST =
      Set.of("egekocabas", "turkerkoc", "gbanu");

  private final NotificationPreferenceRepository prefRepo;
  private final Environment springEnv;

  /**
   * Method to determine if a user can be notified.
   */
  public boolean canNotify(User user,
                           NotificationPreference.Type type) {

    /* global switch & address */
    if (!user.isNotificationsEnabled() || user.getNotificationEmail() == null) {
      return false;
    }

    /* staging allow‑list */
    if (springEnv.acceptsProfiles(Profiles.of("staging"))
        && !STAGING_ALLOWLIST.contains(user.getLogin().toLowerCase())) {
      return false;
    }

    /* per‑type preference */
    return prefRepo
        .findByUserAndType(user, type)
        .map(NotificationPreference::isEnabled)
        .orElse(false);
  }
}