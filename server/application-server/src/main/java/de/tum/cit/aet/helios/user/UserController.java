package de.tum.cit.aet.helios.user;

import de.tum.cit.aet.helios.notification.NotificationPreferenceDto;
import de.tum.cit.aet.helios.notification.NotificationPreferenceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@Log4j2
@RestController
@RequestMapping("api/user")
public class UserController {
  private final UserService userService;
  private final NotificationPreferenceService notificationPreferenceService;

  /**
   * Returns the current user's settings (email and global notifications toggle).
   */
  @GetMapping("/settings")
  public ResponseEntity<UserSettingsDto> getUserSettings() {
    return ResponseEntity.ok(userService.getCurrentUserSettings());
  }

  /**
   * Updates the user's email and/or global notifications toggle.
   */
  @PostMapping("/settings")
  public ResponseEntity<Void> updateUserSettings(@Valid @RequestBody UserSettingsDto dto) {
    userService.updateUserSettings(dto);
    return ResponseEntity.noContent().build();
  }

  /**
   * Returns per-notification type preferences for the current user.
   */
  @GetMapping("/notification-preferences")
  public ResponseEntity<List<NotificationPreferenceDto>> getNotificationPreferences() {
    return ResponseEntity.ok(notificationPreferenceService.getCurrentUserPreferences());
  }

  /**
   * Updates per-notification type preferences for the current user.
   */
  @PostMapping("/notification-preferences")
  public ResponseEntity<Void> updateNotificationPreferences(
      @Valid @RequestBody List<NotificationPreferenceDto> preferences) {
    notificationPreferenceService.updatePreferencesForCurrentUser(preferences);
    return ResponseEntity.noContent().build();
  }
}
