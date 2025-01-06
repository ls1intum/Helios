package de.tum.cit.aet.helios.commit.github;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserConverter;
import de.tum.cit.aet.helios.util.DateUtil;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class GitHubCommitSyncService {

  private final CommitRepository commitRepository;
  private final UserRepository userRepository;
  private final BranchRepository branchRepository;
  private final EnvironmentRepository environmentRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubCommitConverter commitConverter;
  private final GitHubUserConverter userConverter;

  public GitHubCommitSyncService(
      CommitRepository commitRepository,
      GitRepoRepository gitRepoRepository,
      GitHubCommitConverter commitConverter,
      UserRepository userRepository,
      GitHubUserConverter userConverter,
      BranchRepository branchRepository,
      EnvironmentRepository environmentRepository) {
    this.commitRepository = commitRepository;
    this.gitRepoRepository = gitRepoRepository;
    this.commitConverter = commitConverter;
    this.userRepository = userRepository;
    this.userConverter = userConverter;
    this.branchRepository = branchRepository;
    this.environmentRepository = environmentRepository;
  }

  /**
   * Synchronizes all commits from the specified GitHub repositories.
   *
   * @param repositories the list of GitHub repositories to sync commits from
   * @return a list of GitHub commits that were successfully fetched and processed
   */
  public List<GHCommit> syncCommitsOfAllRepositories(List<GHRepository> repositories) {
    return repositories.stream()
        .map(repository -> syncCommitsOfRepository(repository))
        .flatMap(List::stream)
        .toList();
  }

  /**
   * Synchronizes all commits from a specific GitHub repository.
   *
   * @param repository the GitHub repository to sync commits from
   * @return a list of GitHub commits that were successfully fetched and processed
   */
  public List<GHCommit> syncCommitsOfRepository(GHRepository repository) {
    List<GHCommit> commits = new ArrayList<>();

    var dbBranches = branchRepository.findByRepositoryId(repository.getId());
    dbBranches.forEach(
        dbBranch -> {
          try {
            var commit = repository.getCommit(dbBranch.getCommitSha());
            processCommit(commit, repository);
            commits.add(commit);
          } catch (IOException e) {
            log.error(
                "Failed to fetch commit {} of repository {}: {}",
                dbBranch.getCommitSha(),
                repository.getFullName(),
                e.getMessage());
          }
        });

    var dbEnvironments =
        environmentRepository.findByRepositoryIdOrderByCreatedAtDesc(repository.getId());
    dbEnvironments.forEach(
        dbEnvironment -> {
          try {
            if (dbEnvironment.getDeployments() == null
                || dbEnvironment.getDeployments().isEmpty()) {
              return;
            }
            var commit = repository.getCommit(dbEnvironment.getDeployments().getLast().getSha());
            log.info(
                "env: {}, sha: {}",
                dbEnvironment.getName(),
                dbEnvironment.getDeployments().getLast().getSha());
            processCommit(commit, repository);
            commits.add(commit);
          } catch (IOException e) {
            log.error(
                "Failed to fetch commit {} of repository {}: {}",
                dbEnvironment.getDeployments().getLast().getSha(),
                repository.getFullName(),
                e.getMessage());
          }
        });

    // Get all commits for the current repository
    var dbCommits = commitRepository.findByRepositoryId(repository.getId());
    // Delete each commit that exists in the database and not in the fetched commits
    dbCommits.stream()
        .filter(dbCommit -> commits.stream().noneMatch(b -> b.getSHA1().equals(dbCommit.getSha())))
        .forEach(dbCommit -> commitRepository.delete(dbCommit));
    return commits;
  }

  /**
   * Processes a single GitHub commit by updating or creating it in the local repository. Manages
   * associations with repositories.
   *
   * @param ghCommit the GitHub commit to process
   * @param ghRepository the GitHub repository to which the commit belongs
   * @return the updated or newly created Commit entity, or {@code null} if an error occurred
   */
  @Transactional
  public Commit processCommit(GHCommit ghCommit, GHRepository ghRepository) {
    // Link with existing repository if not already linked
    var repository = gitRepoRepository.findByNameWithOwner(ghRepository.getFullName());
    var result =
        commitRepository
            .findByShaAndRepositoryId(ghCommit.getSHA1(), repository.getId())
            .map(
                commit -> {
                  try {
                    log.info(DateUtil.convertToOffsetDateTime(ghCommit.getAuthoredDate()));
                    return commitConverter.update(ghCommit, commit);
                  } catch (Exception e) {
                    log.error("Failed to update commit {}: {}", ghCommit.getSHA1(), e.getMessage());
                    return null;
                  }
                })
            .orElseGet(() -> commitConverter.convert(ghCommit));

    if (result == null) {
      return null;
    }

    if (repository != null) {
      result.setRepository(repository);
    }

    // Link author
    try {
      var author = ghCommit.getAuthor();

      // TODO: Think about a better way to handle anonymous users that committed with a wrong email
      if (author == null) {
        result.setAuthor(
            userRepository
                .findById(Long.parseLong("-1"))
                .orElseGet(() -> userRepository.save(userConverter.convertToAnonymous())));
      } else {
        var resultAuthor =
            userRepository
                .findById(author.getId())
                .orElseGet(() -> userRepository.save(userConverter.convert(author)));
        result.setAuthor(resultAuthor);
      }
    } catch (IOException e) {
      log.error("Failed to link author for commit {}: {}", ghCommit.getSHA1(), e.getMessage());
    }

    return commitRepository.save(result);
  }
}
