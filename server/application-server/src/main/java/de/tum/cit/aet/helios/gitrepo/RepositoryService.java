package de.tum.cit.aet.helios.gitrepo;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RepositoryService {

  private final GitRepoRepository repositoryRepository;

  public RepositoryService(GitRepoRepository repositoryRepository) {
    this.repositoryRepository = repositoryRepository;
  }

  public Optional<RepositoryInfoDto> getRepositoryById(Long id) {
    return repositoryRepository.findById(id).map(RepositoryInfoDto::fromRepository);
  }
}
