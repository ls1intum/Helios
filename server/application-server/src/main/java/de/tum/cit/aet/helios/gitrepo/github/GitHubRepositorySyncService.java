package de.tum.cit.aet.helios.gitrepo.github;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import de.tum.cit.aet.helios.util.DateUtil;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubRepositorySyncService {

  private final GitRepoRepository gitRepoRepository;
  private final GitHubRepositoryConverter repositoryConverter;
  private final GitHubUserSyncService userSyncService;
  private final GitHubService gitHubService;

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

    try {
      var currentRepository = gitHubService.getRepository(ghRepository.getFullName());
      result.setBranchCount(currentRepository.getBranches().size());
      result.setContributors(
           currentRepository.listContributors().withPageSize(6).iterator().nextPage()
              .stream()
              .map(user -> userSyncService.processUser(user))
              .collect(Collectors.toSet()));
      result.setEnvironmentCount(gitHubService.getEnvironments(currentRepository).size());
      if (currentRepository.getLatestRelease() != null) {
        result.setLatestReleaseTagName(currentRepository.getLatestRelease().getTagName());
      }
    } catch (IOException e) {
      log.error("Failed to update repository {}: {}", ghRepository.getId(), e.getMessage());
      throw new RuntimeException(e);
    }

    gitRepoRepository.save(result);
  }
}
