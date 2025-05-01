package de.tum.cit.aet.helios.user;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.userpreference.UserPreferenceRepository;
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
  private final UserPreferenceRepository userPreferenceRepository;
  private final AuthService authService;

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
    } catch (Exception e) {
      log.warn("Failed to set user as logged in", e);
    }
  }

}
