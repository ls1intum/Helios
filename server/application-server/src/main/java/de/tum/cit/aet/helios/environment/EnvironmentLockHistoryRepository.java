package de.tum.cit.aet.helios.environment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface EnvironmentLockHistoryRepository 
    extends JpaRepository<EnvironmentLockHistory, Long> {
  /**
   * Finds the most recent lock history entry for the given environment that is not unlocked
   * yet, and was locked by the specified user.
   */
  EnvironmentLockHistory findTopByEnvironmentAndLockedByAndUnlockedAtIsNullOrderByLockedAtDesc(
      Environment environment,
      String lockedBy
  );
}