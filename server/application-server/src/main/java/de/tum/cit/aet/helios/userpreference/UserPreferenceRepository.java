package de.tum.cit.aet.helios.userpreference;

import de.tum.cit.aet.helios.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
  Optional<UserPreference> findByUser(User user);
}
