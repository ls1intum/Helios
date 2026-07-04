package de.tum.cit.aet.helios.notification;

import de.tum.cit.aet.helios.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, Long> {

  List<NotificationPreference> findByUser(User user);

  Optional<NotificationPreference> findByUserAndType(User user, NotificationPreference.Type type);

  @Query(
      "SELECT p.user FROM NotificationPreference p WHERE p.type = :type AND p.enabled = TRUE")
  List<User> findUsersByTypeEnabled(@Param("type") NotificationPreference.Type type);
}
