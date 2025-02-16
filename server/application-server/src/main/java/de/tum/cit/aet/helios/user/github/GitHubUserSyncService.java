package de.tum.cit.aet.helios.user.github;

import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.util.DateUtil;
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
   *      update.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public User processUser(GHUser ghUser) {
    Long ghUserId = ghUser.getId();
    try {
      // 1. Check if user already exists
      var existingUserOpt = userRepository.findById(ghUserId);
      if (existingUserOpt.isPresent()) {
        // 2. Possibly update existing user if GH data is newer
        return updateExistingUser(existingUserOpt.get(), ghUser);
      } else {
        // 3. Create a new user
        return createNewUser(ghUser);
      }
    } catch (Exception e) {
      log.error("Failed to process GitHub user {}: {}", ghUserId, e.getMessage());
      return null;
    }
  }

  /**
   * Attempt to update an existing user if needed.
   * Catches concurrency exceptions, does fallback read if needed.
   */
  private User updateExistingUser(User existingUser, GHUser ghUser) {
    Long ghUserId = ghUser.getId();
    try {
      // Only update if GH data is newer
      if (existingUser.getUpdatedAt() == null
          || (ghUser.getUpdatedAt() != null
          && existingUser.getUpdatedAt().isBefore(
          DateUtil.convertToOffsetDateTime(ghUser.getUpdatedAt())))) {
        existingUser = userConverter.update(ghUser, existingUser);
      }
      return userRepository.saveAndFlush(existingUser);

    } catch (DataIntegrityViolationException | ConstraintViolationException ex) {
      // concurrency error can happen if we re-insert for some reason
      log.warn("Concurrent update detected for user {}, fallback to read from DB", ghUserId);
      return fallbackExistingUserIfConcurrency(ghUser, ghUserId);

    } catch (Exception e) {
      log.error("Failed to update user {}: {}", ghUserId, e.getMessage());
      // fallback - return the user as-is, ignoring partial changes
      return existingUser;
    }
  }

  /**
   * Attempt to create a new user from GH data.
   * Catches concurrency exceptions, does fallback read if needed.
   */
  private User createNewUser(GHUser ghUser) {
    Long ghUserId = ghUser.getId();
    try {
      User newUser = userConverter.convert(ghUser);
      if (newUser == null) {
        return null; // converter failed, or GH data is invalid
      }
      return userRepository.saveAndFlush(newUser);

    } catch (DataIntegrityViolationException | ConstraintViolationException ex) {
      // Another thread inserted same user ID concurrently
      log.warn("Concurrent insert detected for user {}, will attempt fallback read.", ghUserId);
      return fallbackExistingUserIfConcurrency(ghUser, ghUserId);

    } catch (Exception e) {
      log.error("Failed to create new user {}: {}", ghUserId, e.getMessage());
      return null;
    }
  }

  private User fallbackExistingUserIfConcurrency(
      GHUser ghUser,
      Long ghUserId
  ) {
    // Another thread may have inserted the user in parallel.
    var nowExistingOpt = userRepository.findById(ghUserId);
    if (nowExistingOpt.isPresent()) {
      User nowExisting = nowExistingOpt.get();
      // Update if remote GH data is newer
      try {
        if (ghUser.getUpdatedAt() != null
            && (nowExisting.getUpdatedAt() == null
            || nowExisting.getUpdatedAt().isBefore(
            DateUtil.convertToOffsetDateTime(ghUser.getUpdatedAt())))) {
          nowExisting = userConverter.update(ghUser, nowExisting);
          return userRepository.saveAndFlush(nowExisting);
        }
      } catch (Exception e) {
        log.error("Failed updating existing user after concurrency insert: {}", e.getMessage());
      }
      return nowExisting; // Return whatever we have in DB
    } else {
      // Not found even after concurrency error
      log.warn("User {} not found even after concurrency fallback read", ghUserId);
      return null;
    }
  }



}
