package de.tum.cit.aet.helios.gitrepo;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryService {

  private final GitRepoRepository repositoryRepository;

  public RepositoryService(GitRepoRepository repositoryRepository) {
    this.repositoryRepository = repositoryRepository;
  }

  public List<RepositoryInfoDto> getAllRepositories() {
    return repositoryRepository.findAll().stream().map(RepositoryInfoDto::fromRepository).toList();
  }

  public Optional<RepositoryInfoDto> getRepositoryById(Long id) {
    return repositoryRepository.findByRepositoryId(id)
        .map(RepositoryInfoDto::fromRepository);
  }

  @Transactional
  public void deleteRepository(String nameWithOwner) {
    repositoryRepository.deleteByNameWithOwner(nameWithOwner);
  }
}
