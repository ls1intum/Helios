package de.tum.cit.aet.helios.user.github;

import de.tum.cit.aet.helios.common.util.DateUtil;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.hibernate.exception.ConstraintViolationException;
import org.kohsuke.github.GHUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubUserSyncService {

  private final UserRepository userRepository;
  private final GitHubUserConverter userConverter;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public User getAnonymousUser() {
    Long anonId = -1L;
    try {
      var existingAnonOpt = userRepository.findById(anonId);
      return existingAnonOpt.orElseGet(() -> createAnonymousUser(anonId));
    } catch (Exception e) {
      log.error("Failed to get anonymous user for ID {}: {}", anonId, e.getMessage());
      return null;
    }
  }

  private User createAnonymousUser(Long anonId) {
    try {
      User anonUser = userConverter.convertToAnonymous();
      anonUser = userRepository.saveAndFlush(anonUser);
      return anonUser;
    } catch (DataIntegrityViolationException | ConstraintViolationException ex) {
      // concurrency fallback: someone else created ID -1 in parallel
      log.warn("Concurrent insert detected for anonymous user {}, fallback read.", anonId);
      return userRepository.findById(anonId).orElse(null);
    } catch (Exception e) {
      log.error("Failed to create anonymous user {}: {}", anonId, e.getMessage());
      // fallback read
      return userRepository.findById(anonId).orElse(null);
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
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public User processUser(GHUser ghUser) {
    var result =
        userRepository
            .findById(ghUser.getId())
            .map(
                user -> {
                  try {
                    if (user.getUpdatedAt() == null
                        || (ghUser.getUpdatedAt() != null
                            && user.getUpdatedAt()
                                .isBefore(
                                    DateUtil.convertToOffsetDateTime(ghUser.getUpdatedAt())))) {
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
