package de.tum.cit.aet.helios.user.github;

import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.util.DateUtil;
import jakarta.transaction.Transactional;
import java.io.IOException;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHUser;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class GitHubUserSyncService {

  private final GitHubFacade github;
  private final UserRepository userRepository;
  private final GitHubUserConverter userConverter;

  public GitHubUserSyncService(
      GitHubFacade github, UserRepository userRepository, GitHubUserConverter userConverter) {
    this.github = github;
    this.userRepository = userRepository;
    this.userConverter = userConverter;
  }

  /** Sync all existing users in the local repository with their GitHub data. */
  public void syncAllExistingUsers() {
    userRepository.findAll().stream().map(User::getLogin).forEach(this::syncUser);
  }

  /**
   * Sync a GitHub user's data by their login and processes it to synchronize with the local
   * repository.
   *
   * @param login The GitHub username (login) of the user to fetch.
   */
  public void syncUser(String login) {
    try {
      processUser(github.getUser(login));
    } catch (IOException e) {
      log.error("Failed to fetch user {}: {}", login, e.getMessage());
    }
  }

  /**
   * Processes a GitHub user by either updating the existing user in the repository or creating a
   * new one.
   *
   * @param ghUser The GitHub user data to process.
   * @return The updated or newly created User entity, or {@code null} if an error occurred during
   *     update.
   */
  @Transactional
  public User processUser(GHUser ghUser) {
    var result =
        userRepository
            .findById(ghUser.getId())
            .map(
                user -> {
                  try {
                    if (user.getUpdatedAt() == null
                        || user.getUpdatedAt()
                            .isBefore(DateUtil.convertToOffsetDateTime(ghUser.getUpdatedAt()))) {
                      return userConverter.update(ghUser, user);
                    }
                    return user;
                  } catch (IOException e) {
                    log.error("Failed to update repository {}: {}", ghUser.getId(), e.getMessage());
                    return null;
                  }
                })
            .orElseGet(() -> userConverter.convert(ghUser));

    if (result == null) {
      return null;
    }

    return userRepository.save(result);
  }
}
