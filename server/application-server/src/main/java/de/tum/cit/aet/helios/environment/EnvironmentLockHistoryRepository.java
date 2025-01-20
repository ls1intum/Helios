package de.tum.cit.aet.helios.environment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EnvironmentLockHistoryRepository
    extends JpaRepository<EnvironmentLockHistory, Long> {

  /**
   * Finds the most recent lock history entry for a specific environment that is not unlocked
   * yet and was locked by the specified user.
   *
   * @param environment the environment for which to find the lock history
   * @param lockedBy    the user who locked the environment
   * @return the most recent lock history entry matching the criteria, or null if none exists
   */
  @Query("""
        SELECT elh
        FROM EnvironmentLockHistory elh
        WHERE elh.environment = :environment
          AND elh.environment.enabled = true
          AND elh.lockedBy = :lockedBy
          AND elh.unlockedAt IS NULL
        ORDER BY elh.lockedAt DESC
        LIMIT 1
      """)
  Optional<EnvironmentLockHistory> findLatestLockForEnvironmentAndUser(
      @Param("environment") Environment environment,
      @Param("lockedBy") String lockedBy
  );

  /**
   * Finds the most recent lock history entry for an enabled environment that is not unlocked
   * yet, and was locked by the specified user.
   *
   * @param lockedBy the user who locked the environment
   * @return the most recent lock history entry matching the criteria, or null if none exists
   */
  @Query("""
        SELECT elh
        FROM EnvironmentLockHistory elh
        WHERE elh.environment.enabled = true
          AND elh.lockedBy = :lockedBy
          AND elh.unlockedAt IS NULL
        ORDER BY elh.lockedAt ASC
        LIMIT 1
      """)
  Optional<EnvironmentLockHistory> findLatestLockForEnabledEnvironment(
      @Param("lockedBy") String lockedBy);

  /**
   * Returns all lock history entries for the given environment ID,
   * sorted by lockedAt in descending order.
   */
  @Query("""
        SELECT elh
        FROM EnvironmentLockHistory elh
        WHERE elh.environment.id = :environmentId
        ORDER BY elh.lockedAt DESC
      """)
  List<EnvironmentLockHistory> findLockHistoriesByEnvironment(
      @Param("environmentId") Long environmentId
  );
}