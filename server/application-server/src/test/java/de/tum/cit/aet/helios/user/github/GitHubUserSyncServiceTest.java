package de.tum.cit.aet.helios.user.github;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.User.Type;
import de.tum.cit.aet.helios.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHUser;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GitHubUserSyncServiceTest {

  @Mock private UserRepository userRepository;

  private final GitHubUserConverter userConverter = new GitHubUserConverter();

  @Test
  void shouldProcessExistingCopilotUserWithoutCallingGhUserUpdatedAt() throws Exception {
    GitHubUserSyncService service = new GitHubUserSyncService(userRepository, userConverter);
    long ghId = 198982749L;
    User existingUser = createExistingUser(ghId, "Copilot");
    GHUser ghUser = createGhUserWithBrokenUpdatedAt(ghId, "Copilot",
        "https://avatars.githubusercontent.com/u/198982749?v=4");

    when(userRepository.findById(ghId)).thenReturn(Optional.of(existingUser));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User result = service.processUser(ghUser);

    assertNotNull(result);
    assertTrue(userConverter.isCopilotActorLogin(result.getLogin()));
    assertFalse(result.getAvatarUrl().isBlank());
    assertNull(result.getUpdatedAt());
    verify(userRepository).save(any(User.class));
  }

  @Test
  void shouldProcessExistingCopilotUserCaseInsensitivelyWithoutCallingGhUserUpdatedAt()
      throws Exception {
    GitHubUserSyncService service = new GitHubUserSyncService(userRepository, userConverter);
    long ghId = 198982750L;
    User existingUser = createExistingUser(ghId, "copilot");
    GHUser ghUser = createGhUserWithBrokenUpdatedAt(ghId, "COPILOT",
        "https://avatars.githubusercontent.com/u/198982750?v=4");

    when(userRepository.findById(ghId)).thenReturn(Optional.of(existingUser));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User result = service.processUser(ghUser);

    assertNotNull(result);
    assertTrue(userConverter.isCopilotActorLogin(result.getLogin()));
    assertFalse(result.getAvatarUrl().isBlank());
    assertNull(result.getUpdatedAt());
    verify(userRepository).save(any(User.class));
  }

  private static User createExistingUser(long id, String login) {
    User user = new User();
    user.setId(id);
    user.setLogin(login);
    user.setAvatarUrl("https://avatars.githubusercontent.com/u/" + id + "?v=4");
    user.setName(login);
    user.setHtmlUrl("https://github.com/" + login);
    user.setType(Type.USER);
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now().minusHours(1));
    return user;
  }

  private static GHUser createGhUserWithBrokenUpdatedAt(long id, String login, String avatarUrl)
      throws Exception {
    GHUser user = new GHUser();

    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "login", login);
    ReflectionTestUtils.setField(user, "avatar_url", avatarUrl);
    ReflectionTestUtils.setField(user, "html_url", "https://github.com/" + login);
    // Mark as populated to avoid any API fetch in GHPerson.populate().
    ReflectionTestUtils.setField(user, "createdAt", "2026-01-10T00:00:00Z");
    // This value triggers parsing failure if GHUser.getUpdatedAt() is evaluated.
    ReflectionTestUtils.setField(user, "updatedAt", "not-a-valid-date");
    return user;
  }
}
