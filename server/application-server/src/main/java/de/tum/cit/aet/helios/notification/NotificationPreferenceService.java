package de.tum.cit.aet.helios.notification;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationPreferenceService {

  private final NotificationPreferenceRepository repository;
  private final AuthService authService;

  public void initializeDefaultsForUser(User user) {
    for (NotificationPreference.Type type : NotificationPreference.Type.values()) {
      boolean exists = repository.findByUserAndType(user, type).isPresent();
      if (!exists) {
        repository.save(new NotificationPreference(user, type, true));
      }
    }
  }

  public List<NotificationPreferenceDto> getCurrentUserPreferences() {
    User user = authService.getUserFromGithubId();
    return repository.findByUser(user).stream()
        .map(pref -> new NotificationPreferenceDto(pref.getType(), pref.isEnabled()))
        .toList();
  }

  public void updatePreferencesForCurrentUser(List<NotificationPreferenceDto> preferences) {
    User user = authService.getUserFromGithubId();
    for (NotificationPreferenceDto dto : preferences) {
      NotificationPreference pref = repository
          .findByUserAndType(user, dto.type())
          .orElse(new NotificationPreference(user, dto.type(), true));
      pref.setEnabled(dto.enabled());
      repository.save(pref);
    }
  }
}