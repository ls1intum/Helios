package de.tum.cit.aet.helios.branch.github;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserConverter;
import de.tum.cit.aet.helios.util.DateUtil;
import jakarta.transaction.Transactional;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

/**
 * Service for synchronizing branches from GitHub repositories.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubBranchSyncService {

  private final BranchRepository branchRepository;
  private final GitRepoRepository gitRepoRepository;
  private final UserRepository userRepository;
  private final GitHubBranchConverter branchConverter;
  private final GitHubUserConverter userConverter;


  /**
   * Processes a single GitHub branch by updating or creating it in the local repository. Manages
   * associations with repositories.
   *
   * @param ghBranch the GitHub branch to process
   */
  @Transactional
  public void processBranch(GHBranch ghBranch) {
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
      return;
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

    branchRepository.save(result);
  }
}
