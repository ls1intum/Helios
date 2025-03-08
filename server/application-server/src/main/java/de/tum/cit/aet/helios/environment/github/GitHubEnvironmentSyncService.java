package de.tum.cit.aet.helios.environment.github;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubEnvironmentSyncService {

  private final EnvironmentRepository environmentRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubEnvironmentConverter environmentConverter;

  /**
   * Processes a single GitHubEnvironmentDto by updating or creating it in the local repository.
   *
   * @param gitHubEnvironmentDto the GitHubEnvironmentDto to process
   * @param ghRepository the GitHub repository the environment belongs to
   */
  @Transactional
  public void processEnvironment(
      @NotNull GitHubEnvironmentDto gitHubEnvironmentDto, @NotNull GHRepository ghRepository) {
    Environment environment =
        environmentRepository.findById(gitHubEnvironmentDto.getId()).orElseGet(Environment::new);

    // Convert Dto to Entity
    environmentConverter.update(gitHubEnvironmentDto, environment);

    // Link the environment to the repository
    String fullName = ghRepository.getFullName();
    GitRepository repository = gitRepoRepository.findByNameWithOwner(fullName);
    if (repository != null) {
      environment.setRepository(repository);
    } else {
      log.warn("Repository {} not found in local database.", fullName);
    }

    // Save the environment
    environmentRepository.save(environment);
  }
}
