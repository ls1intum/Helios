package de.tum.cit.aet.helios.branch.github;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserConverter;
import de.tum.cit.aet.helios.util.DateUtil;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

/**
 * Service for synchronizing branches from GitHub repositories.
 */
@Service
@Log4j2
public class GitHubBranchSyncService {

  private final BranchRepository branchRepository;
  private final GitRepoRepository gitRepoRepository;
  private final UserRepository userRepository;
  private final GitHubBranchConverter branchConverter;
  private final GitHubUserConverter userConverter;

  public GitHubBranchSyncService(
      BranchRepository branchRepository,
      GitRepoRepository gitRepoRepository,
      GitHubBranchConverter branchConverter,
      UserRepository userRepository,
      GitHubUserConverter userConverter) {
    this.branchRepository = branchRepository;
    this.gitRepoRepository = gitRepoRepository;
    this.branchConverter = branchConverter;
    this.userRepository = userRepository;
    this.userConverter = userConverter;
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
      branches.forEach(branch -> processBranch(branch));
      // Get all branches for the current repository
      // var dbBranches = branchRepository.findByRepositoryRepositoryId(repository.getId());
      // TODO: We might need the old branches in some cases, so we should not delete them for now
      // Delete each branch that exists in the database and not in the fetched branches
      // dbBranches.stream()
      //   .filter(
      //      dbBranch -> branches.stream().noneMatch(b -> b.getName().equals(dbBranch.getName())))
      //     .forEach(dbBranch -> branchRepository.delete(dbBranch));
      return branches;
    } catch (IOException e) {
      log.error(
          "Failed to fetch branches of repository {}: {}",
          repository.getFullName(),
          e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * Processes a single GitHub branch by updating or creating it in the local repository. Manages
   * associations with repositories.
   *
   * @param ghBranch the GitHub branch to process
   * @return the updated or newly created Branch entity, or {@code null} if an error occurred
   */
  @Transactional
  public Branch processBranch(GHBranch ghBranch) {
    // Link with existing repository if not already linked
    GHRepository ghRepository = ghBranch.getOwner();
    var repository = gitRepoRepository.findByNameWithOwner(ghRepository.getFullName());

    var result =
        branchRepository
            .findByNameAndRepositoryRepositoryId(ghBranch.getName(), repository.getRepositoryId())
            .map(
                branch -> {
                  try {
                    return branchConverter.update(ghBranch, branch);
                  } catch (Exception e) {
                    log.error("Failed to update branch {}: {}", ghBranch.getName(), e.getMessage());
                    return null;
                  }
                })
            .orElseGet(() -> branchConverter.convert(ghBranch));

    if (result == null) {
      return null;
    }

    if (repository != null) {
      result.setRepository(repository);
    }

    result.setDefault(ghRepository.getDefaultBranch().equals(ghBranch.getName()));

    // Set branch comparison (to main) data
    try {
      var branchCompare =
          ghRepository.getCompare(ghRepository.getDefaultBranch(), ghBranch.getName());
      result.setAheadBy(branchCompare.getAheadBy());
      result.setBehindBy(branchCompare.getBehindBy());
    } catch (IOException e) {
      log.error(
          "Failed to update branch comparison data {}: {}", ghBranch.getName(), e.getMessage());
    }

    // Link updatedBy user
    try {
      var updatedBy = ghRepository.getCommit(ghBranch.getSHA1()).getCommitter();

      // TODO: Think about a better way to handle anonymous users that committed with a wrong email
      if (updatedBy == null) {
        result.setUpdatedBy(
            userRepository
                .findById(Long.parseLong("-1"))
                .orElseGet(() -> userRepository.save(userConverter.convertToAnonymous())));
        result.setUpdatedAt(
            DateUtil.convertToOffsetDateTime(
                ghRepository.getCommit(ghBranch.getSHA1()).getCommitShortInfo().getCommitDate()));
      } else {
        var resultUpdatedBy =
            userRepository
                .findById(updatedBy.getId())
                .orElseGet(() -> userRepository.save(userConverter.convert(updatedBy)));
        result.setUpdatedBy(resultUpdatedBy);
        result.setUpdatedAt(DateUtil.convertToOffsetDateTime(updatedBy.getUpdatedAt()));
      }
    } catch (IOException e) {
      log.error(
          "Failed to link updatedBy user for pull request {}: {}",
          ghBranch.getName(),
          e.getMessage());
    }

    return branchRepository.save(result);
  }
}
