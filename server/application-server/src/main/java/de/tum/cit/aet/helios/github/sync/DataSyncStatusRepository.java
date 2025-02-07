package de.tum.cit.aet.helios.github.sync;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataSyncStatusRepository extends JpaRepository<DataSyncStatus, Long> {

  Optional<DataSyncStatus> findTopByRepositoryNameWithOwner(String repositoryNameWithOwner);
}
