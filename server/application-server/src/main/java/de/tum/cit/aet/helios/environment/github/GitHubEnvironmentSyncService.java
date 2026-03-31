package de.tum.cit.aet.helios.environment.github;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

    // Update protection rules
    environmentConverter.updateProtectionRules(gitHubEnvironmentDto, environment);
  }

  /**
   * Removes environments from the local database that no longer exist in GitHub.
   *
   * <p>This method compares the environments currently in GitHub with those stored locally for a
   * given repository. Any local environments that are not present in the GitHub list will be
   * deleted.
   *
   * @param gitHubEnvironments the list of environments currently in GitHub
   * @param repositoryId the ID of the repository to clean up environments for
   */
  @Transactional
  public void removeDeletedEnvironments(
      @NotNull List<GitHubEnvironmentDto> gitHubEnvironments, @NotNull Long repositoryId) {
    Set<Long> githubEnvironmentIds =
        gitHubEnvironments.stream().map(GitHubEnvironmentDto::getId).collect(Collectors.toSet());

    List<Environment> localEnvironments =
        environmentRepository.findByRepositoryRepositoryIdOrderByCreatedAtDesc(repositoryId);

    List<Environment> environmentsToDelete =
        localEnvironments.stream()
            .filter(env -> !githubEnvironmentIds.contains(env.getId()))
            .toList();

    for (Environment environment : environmentsToDelete) {
      log.info(
          "Deleting environment '{}' (ID: {}) as it no longer exists in GitHub repository (ID:"
              + " {}).",
          environment.getName(),
          environment.getId(),
          repositoryId);

      environmentRepository.delete(environment);
    }

    if (!environmentsToDelete.isEmpty()) {
      log.info(
          "Removed {} environment(s) from repository (ID: {}) that no longer exist in GitHub.",
          environmentsToDelete.size(),
          repositoryId);
    }
  }
}
