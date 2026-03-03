package de.tum.cit.aet.helios.user.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tum.cit.aet.helios.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHUser;
import org.springframework.test.util.ReflectionTestUtils;

class GitHubUserConverterTest {

  private GitHubUserConverter converter;

  @BeforeEach
  void setUp() {
    converter = new GitHubUserConverter();
  }

  @Test
  void shouldIdentifyCopilotLoginCaseInsensitively() {
    assertTrue(converter.isCopilotActorLogin("Copilot"));
    assertTrue(converter.isCopilotActorLogin("copilot"));
    assertTrue(converter.isCopilotActorLogin("COPILOT"));
  }

  @Test
  void shouldNotIdentifyOtherLoginsAsCopilotActor() {
    assertFalse(converter.isCopilotActorLogin(null));
    assertFalse(converter.isCopilotActorLogin(""));
    assertFalse(converter.isCopilotActorLogin("github-copilot"));
    assertFalse(converter.isCopilotActorLogin("octocat"));
  }

  @Test
  void shouldCreateAnonymousUserWithExpectedDefaults() {
    User user = converter.convertToAnonymous();

    assertEquals(-1L, user.getId());
    assertEquals("Anonymous", user.getName());
    assertEquals("anonymous", user.getLogin());
    assertEquals(User.Type.USER, user.getType());
    assertEquals(
        "https://github.githubassets.com/images/gravatars/gravatar-user-420.png?size=40",
        user.getAvatarUrl());
    assertEquals("https://helios.aet.cit.tum.de/not-found", user.getHtmlUrl());
    assertNotNull(user.getCreatedAt());
    assertNotNull(user.getUpdatedAt());
  }

  @Test
  void shouldCreateExpirationPickerUserWithExpectedDefaults() {
    User user = converter.convertToExpirationPicker();

    assertEquals(-2L, user.getId());
    assertEquals("Auto Release Lock", user.getName());
    assertEquals("ls1intum/Helios", user.getLogin());
    assertEquals(User.Type.USER, user.getType());
    assertEquals("https://helios.aet.cit.tum.de/favicon.png", user.getAvatarUrl());
    assertEquals("https://helios.aet.cit.tum.de/not-found", user.getHtmlUrl());
    assertNotNull(user.getCreatedAt());
    assertNotNull(user.getUpdatedAt());
  }

  @Test
  void shouldLeaveUpdatedAtUnsetForCopilotActor() {
    GHUser ghUser = new GHUser();
    ReflectionTestUtils.setField(ghUser, "id", 198982749L);
    ReflectionTestUtils.setField(ghUser, "login", "Copilot");
    ReflectionTestUtils.setField(
        ghUser, "avatar_url", "https://avatars.githubusercontent.com/u/198982749?v=4");
    ReflectionTestUtils.setField(ghUser, "html_url", "https://github.com/Copilot");

    User user = converter.update(ghUser, new User());

    assertEquals(198982749L, user.getId());
    assertEquals("Copilot", user.getLogin());
    assertEquals(User.Type.BOT, user.getType());
    assertNull(user.getUpdatedAt());
    assertNotNull(user.getCreatedAt());
  }
}
