package de.tum.cit.aet.helios.gitrepo.github;

import de.tum.cit.aet.helios.common.util.DateUtil;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubRepositorySyncService {

  private final GitRepoRepository gitRepoRepository;
  private final GitHubRepositoryConverter repositoryConverter;

  /**
   * Processes a single GitHub repository by updating or creating it in the local repository.
   *
   * @param ghRepository The GitHub repository data to process.
   */
  @Transactional
  public void processRepository(GHRepository ghRepository) {
    // TODO if gitRepoSettings is not found, create it
    var result =
        gitRepoRepository
            .findByRepositoryId(ghRepository.getId())
            .map(
                repository -> {
                  try {
                    if (repository.getUpdatedAt() == null
                        || repository
                            .getUpdatedAt()
                            .isBefore(
                                DateUtil.convertToOffsetDateTime(ghRepository.getUpdatedAt()))) {
                      return repositoryConverter.update(ghRepository, repository);
                    }
                    return repository;
                  } catch (IOException e) {
                    log.error(
                        "Failed to update repository {}: {}", ghRepository.getId(), e.getMessage());
                    return null;
                  }
                })
            .orElseGet(() -> repositoryConverter.convert(ghRepository));

    if (result == null) {
      return;
    }

    gitRepoRepository.save(result);
  }
}
