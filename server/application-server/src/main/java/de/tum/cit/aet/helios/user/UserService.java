package de.tum.cit.aet.helios.user;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.notification.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Log4j2
public class UserService {

  private final UserRepository userRepository;
  private final AuthService authService;
  private final NotificationPreferenceService notificationPreferenceService;

  /**
   * Handles user-related setup on their first login to Helios.
   * Sets hasLoggedIn = true and initializes notification email if applicable.
   */
  public void handleFirstLogin() {
    try {
      User loggedInUser = authService.isLoggedIn() ? authService.getUserFromGithubId() : null;
      if (loggedInUser == null || loggedInUser.isHasLoggedIn()) {
        return;
      }

      // Set the user's loggedIn as true
      loggedInUser.setHasLoggedIn(true);
      // Set notificationsEnabled to true
      loggedInUser.setNotificationsEnabled(true);

      // Set the notification email to the user's email
      if (loggedInUser.getNotificationEmail() == null
          && StringUtils.isNotBlank(loggedInUser.getEmail())) {
        loggedInUser.setNotificationEmail(loggedInUser.getEmail());
      }

      userRepository.save(loggedInUser);

      // Initialize notification preferences for the user
      notificationPreferenceService.initializeDefaultsForUser(loggedInUser);
    } catch (Exception e) {
      log.warn("Failed to set user as logged in", e);
    }
  }

  /**
   * Retrieves the current user's settings (email and global notifications toggle).
   */
  public UserSettingsDto getCurrentUserSettings() {
    User user = authService.getUserFromGithubId();
    return new UserSettingsDto(user.getNotificationEmail(), user.isNotificationsEnabled());
  }

  /**
   * Updates the user's email and/or global notifications toggle.
   */
  public void updateUserSettings(UserSettingsDto dto) {
    User user = authService.getUserFromGithubId();
    if (StringUtils.isNotBlank(dto.notificationEmail())) {
      user.setNotificationEmail(dto.notificationEmail());
    }
    if (dto.notificationsEnabled() != null) {
      user.setNotificationsEnabled(dto.notificationsEnabled());
    }
    userRepository.save(user);
  }

}
