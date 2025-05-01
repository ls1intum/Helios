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
   * Sets the user as logged in. This method should be called when the user logs in to the system.
   * It updates the user's hasLoggedIn status and sets the notification email if it is not already
   * set. These actions are performed only once.
   */
  public void setUserLoggedIn() {
    try {
      User loggedInUser = authService.isLoggedIn() ? authService.getUserFromGithubId() : null;
      if (loggedInUser == null || loggedInUser.isHasLoggedIn()) {
        return;
      }

      loggedInUser.setHasLoggedIn(true);
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
