package de.tum.cit.aet.helios.gitrepo;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@RequiredArgsConstructor
@Service
public class RepositoryService {

  private final GitRepoRepository repositoryRepository;

  public List<RepositoryInfoDto> getAllRepositories() {
    return repositoryRepository.findAllByOrderByUpdatedAtDesc().stream()
        .map(RepositoryInfoDto::fromRepository).toList();
  }

  public Optional<RepositoryInfoDto> getRepositoryById(Long id) {
    return repositoryRepository.findByRepositoryId(id).map(RepositoryInfoDto::fromRepository);
  }

  @Transactional
  public void deleteRepository(String nameWithOwner) {
    log.warn("Deleting repository {}", nameWithOwner);
    repositoryRepository.deleteByNameWithOwner(nameWithOwner);
  }
}
