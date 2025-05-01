package de.tum.cit.aet.helios.notification;

import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.userpreference.UserPreference;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, Long> {

  Optional<NotificationPreference> findByUser(User user);

  Optional<NotificationPreference> findByUserAndType(User user, NotificationPreference.Type type);
}
