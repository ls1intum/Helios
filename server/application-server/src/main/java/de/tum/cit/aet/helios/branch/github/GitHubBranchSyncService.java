package de.tum.cit.aet.helios.branch.github;


import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.branch.Branch;

import java.io.IOException;
import java.util.*;

/**
 * Service for synchronizing branches from GitHub repositories.
 */
@Service
@Log4j2
public class GitHubBranchSyncService {

    private final BranchRepository branchRepository;
    private final GitRepoRepository gitRepoRepository;
    private final GitHubBranchConverter branchConverter;

    public GitHubBranchSyncService(
            BranchRepository branchRepository,
            GitRepoRepository gitRepoRepository,
            GitHubBranchConverter branchConverter) {
        this.branchRepository = branchRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.branchConverter = branchConverter;
    }

    /**
     * Synchronizes all branches from the specified GitHub repositories.
     *
     * @param repositories the list of GitHub repositories to sync branches from
     * @return a list of GitHub branches that were successfully fetched and processed
     */
    public List<GHBranch> syncBranchesOfAllRepositories(List<GHRepository> repositories) {
        return repositories.stream()
                .map(repository -> syncBranchesOfRepository(repository))
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Synchronizes all branches from a specific GitHub repository.
     *
     * @param repository the GitHub repository to sync branches from
     * @return a list of GitHub branches that were successfully fetched and processed
     */
    public List<GHBranch> syncBranchesOfRepository(GHRepository repository) {
        try {
            var branches = repository.getBranches().values().stream().toList();
            branches.forEach(branch -> processBranch(branch, repository));
            // Get all branches for the current repository
            var dbBranches = branchRepository.findByRepositoryId(repository.getId());
            // Delete each branch that exists in the database and not in the fetched branches
            dbBranches.stream().filter(dbBranch -> branches.stream().noneMatch(b -> b.getName().equals(dbBranch.getName())))
                    .forEach(dbBranch -> branchRepository.delete(dbBranch));
            return branches;
        } catch (IOException e) {
            log.error("Failed to fetch branches of repository {}: {}", repository.getFullName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Processes a single GitHub branch by updating or creating it in the local repository.
     * Manages associations with repositories.
     *
     * @param ghBranch the GitHub branch to process
     * @param ghRepository the GitHub repository to which the branch belongs
     * @return the updated or newly created Branch entity, or {@code null} if an error occurred
     */
    @Transactional
    public Branch processBranch(GHBranch ghBranch, GHRepository ghRepository) {
        // Link with existing repository if not already linked
        var repository = gitRepoRepository.findByNameWithOwner(ghRepository.getFullName());

        var result = branchRepository.findByNameAndRepositoryId(ghBranch.getName(), repository.getId())
                .map(branch -> {
                    try {
                        return branchConverter.update(ghBranch, branch);
                    } catch (Exception e) {
                        log.error("Failed to update branch {}: {}", ghBranch.getName(), e.getMessage());
                        return null;
                    }
                }).orElseGet(() -> branchConverter.convert(ghBranch));

        if (result == null) {
            return null;
        }

        if (repository != null) {
            result.setRepository(repository);
        }
        return branchRepository.save(result);
    }
}
