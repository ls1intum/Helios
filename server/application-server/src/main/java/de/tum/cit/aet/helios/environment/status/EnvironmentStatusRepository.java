package de.tum.cit.aet.helios.environment.status;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnvironmentStatusRepository extends JpaRepository<EnvironmentStatus, Long> {
  @Query("SELECT s FROM EnvironmentStatus s WHERE s.environment.id = ?1 ORDER BY s.checkTimestamp DESC")
  Page<EnvironmentStatus> findLatestStatuses(Long environmentId, Pageable pageable);
}
