package de.tum.cit.aet.helios.environment.github;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.GitHubEnvironment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@Log4j2
public class GitHubEnvironmentSyncService {

    private final EnvironmentRepository environmentRepository;
    private final GitRepoRepository gitRepoRepository;
    private final GitHubService gitHubService;
    private final GitHubEnvironmentConverter environmentConverter;

    public GitHubEnvironmentSyncService(EnvironmentRepository environmentRepository,
                                        GitRepoRepository gitRepoRepository,
                                        GitHubService gitHubService,
                                        GitHubEnvironmentConverter environmentConverter) {
        this.environmentRepository = environmentRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.gitHubService = gitHubService;
        this.environmentConverter = environmentConverter;
    }

    /**
     * Synchronizes all environments from the specified GitHub repositories.
     *
     * @param repositories the list of GitHub repositories to sync environments from
     */
    public void syncEnvironmentsOfAllRepositories(@NotNull List<GHRepository> repositories) {
        repositories.forEach(this::syncEnvironmentsOfRepository);
    }

    /**
     * Synchronizes all environments from a specific GitHub repository.
     *
     * @param ghRepository the GitHub repository to sync environments from
     */
    public void syncEnvironmentsOfRepository(GHRepository ghRepository) {
        try {
            List<GitHubEnvironment> gitHubEnvironments = gitHubService.getEnvironments(ghRepository);

            for (GitHubEnvironment gitHubEnvironment : gitHubEnvironments) {
                processEnvironment(gitHubEnvironment, ghRepository);
            }
        } catch (IOException e) {
            log.error("Failed to sync environments for repository {}: {}", ghRepository.getFullName(), e.getMessage());
        }
    }

    /**
     * Processes a single GitHubEnvironment by updating or creating it in the local repository.
     *
     * @param gitHubEnvironment the GitHubEnvironment to process
     * @param ghRepository   the GitHub repository the environment belongs to
     */
    private void processEnvironment(@NotNull GitHubEnvironment gitHubEnvironment, @NotNull GHRepository ghRepository) {
        Environment environment = environmentRepository.findById(gitHubEnvironment.getId())
                .orElseGet(Environment::new);

        // Convert DTO to Entity
        environmentConverter.update(gitHubEnvironment, environment);

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
