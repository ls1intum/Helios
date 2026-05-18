package de.tum.cit.aet.helios.workflow.queue;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RunnerRepository extends JpaRepository<Runner, Long> {

  List<Runner> findByStatus(Runner.Status status);

  @Modifying
  @Query(
      "UPDATE Runner r SET r.status = 'OFFLINE', r.offlineSince = :now "
          + "WHERE r.status = 'ONLINE' AND r.id NOT IN :seenIds")
  int markMissingOffline(
      @Param("seenIds") List<Long> seenIds, @Param("now") OffsetDateTime now);
}
