package de.tum.cit.aet.helios.github.sync;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class DataSyncStatusService {

  private final DataSyncStatusRepository dataSyncStatusRepository;

  @Transactional
  public void deleteByRepositoryNameWithOwner(String fullName) {
    log.warn("Deleting data sync status for repository {}", fullName);
    dataSyncStatusRepository.deleteByRepositoryNameWithOwner(fullName);
  }
}
