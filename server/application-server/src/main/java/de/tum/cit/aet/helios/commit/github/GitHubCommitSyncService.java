package de.tum.cit.aet.helios.commit.github;

import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserConverter;
import de.tum.cit.aet.helios.util.DateUtil;
import jakarta.transaction.Transactional;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubCommitSyncService {

  private final CommitRepository commitRepository;
  private final UserRepository userRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubCommitConverter commitConverter;
  private final GitHubUserConverter userConverter;

  /**
   * Processes a single GitHub commit by updating or creating it in the local repository. Manages
   * associations with repositories.
   *
   * @param ghCommit     the GitHub commit to process
   * @param ghRepository the GitHub repository to which the commit belongs
   */
  @Transactional
  public void processCommit(GHCommit ghCommit, GHRepository ghRepository) {
    // Link with existing repository if not already linked
    var repository = gitRepoRepository.findByNameWithOwner(ghRepository.getFullName());
    var result =
        commitRepository
            .findByShaAndRepositoryRepositoryId(ghCommit.getSHA1(), repository.getRepositoryId())
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
      return;
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

    commitRepository.save(result);
  }
}
