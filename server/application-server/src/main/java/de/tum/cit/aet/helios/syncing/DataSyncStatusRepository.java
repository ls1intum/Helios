package de.tum.cit.aet.helios.syncing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataSyncStatusRepository extends JpaRepository<DataSyncStatus, Long> {

    public Optional<DataSyncStatus> findTopByOrderByStartTimeDesc();
}