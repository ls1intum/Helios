package de.tum.cit.aet.helios.gitrepo.github;

import jakarta.transaction.Transactional;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.util.DateUtil;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class GitHubRepositorySyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubRepositorySyncService.class);

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    private final GitHub github;
    private final GitRepoRepository gitRepoRepository;
    private final GitHubRepositoryConverter repositoryConverter;

    public GitHubRepositorySyncService(
            GitHub github,
            GitRepoRepository gitRepoRepository,
            GitHubRepositoryConverter repositoryConverter) {
        this.github = github;
        this.gitRepoRepository = gitRepoRepository;
        this.repositoryConverter = repositoryConverter;
    }

    /**
     * Syncs all monitored GitHub repositories.
     *
     * @return A list of successfully fetched GitHub repositories.
     */
    public List<GHRepository> syncAllMonitoredRepositories() {
        return syncAllRepositories(List.of(repositoriesToMonitor));
    }

    /**
     * Syncs all repositories owned by a specific GitHub user or organization.
     *
     * @param owner The GitHub username (login) of the repository owner.
     */
    public void syncAllRepositoriesOfOwner(String owner) {
        var builder = github.searchRepositories().user(owner);
        var iterator = builder.list().withPageSize(100).iterator();
        while (iterator.hasNext()) {
            var ghRepositories = iterator.nextPage();
            ghRepositories.forEach(this::processRepository);
        }
    }

    /**
     * Syncs a list of repositories specified by their full names (e.g.,
     * "owner/repo").
     *
     * @param nameWithOwners A list of repository full names in the format
     *                       "owner/repo".
     * @return A list of successfully fetched GitHub repositories.
     */
    public List<GHRepository> syncAllRepositories(List<String> nameWithOwners) {
        return nameWithOwners.stream()
                .map(this::syncRepository)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * Syncs a single GitHub repository by its full name (e.g., "owner/repo").
     *
     * @param nameWithOwner The full name of the repository in the format
     *                      "owner/repo".
     * @return An optional containing the fetched GitHub repository, or an empty
     *         optional if the repository could not be fetched.
     */
    public Optional<GHRepository> syncRepository(String nameWithOwner) {
        try {
            var repository = github.getRepository(nameWithOwner);
            processRepository(repository);
            return Optional.of(repository);
        } catch (IOException e) {
            logger.error("Failed to fetch repository {}: {}", nameWithOwner, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Processes a single GitHub repository by updating or creating it in the local
     * repository.
     *
     * @param ghRepository The GitHub repository data to process.
     * @return The updated or newly created Repository entity, or {@code null} if an
     *         error occurred during update.
     */
    @Transactional
    public GitRepository processRepository(GHRepository ghRepository) {
        var result = gitRepoRepository.findById(ghRepository.getId())
                .map(repository -> {
                    try {
                        if (repository.getUpdatedAt() == null || repository.getUpdatedAt()
                                .isBefore(DateUtil.convertToOffsetDateTime(ghRepository.getUpdatedAt()))) {
                            return repositoryConverter.update(ghRepository, repository);
                        }
                        return repository;
                    } catch (IOException e) {
                        logger.error("Failed to update repository {}: {}", ghRepository.getId(), e.getMessage());
                        return null;
                    }
                }).orElseGet(() -> repositoryConverter.convert(ghRepository));

        if (result == null) {
            return null;
        }

        return gitRepoRepository.save(result);
    }
}
