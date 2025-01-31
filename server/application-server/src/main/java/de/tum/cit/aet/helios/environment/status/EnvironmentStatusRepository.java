package de.tum.cit.aet.helios.environment.status;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EnvironmentStatusRepository extends JpaRepository<EnvironmentStatus, Long> {
  @Modifying
  @Query("DELETE FROM EnvironmentStatus es "
      + "WHERE es.environment.id = :environmentId AND es.id NOT IN ("
      + "SELECT es2.id FROM EnvironmentStatus es2 WHERE es2.environment.id = :environmentId "
      + "ORDER BY es2.checkTimestamp DESC LIMIT :keepCount)")
  void deleteAllButOldestByEnvironmentId(Long environmentId, int keepCount);
}
