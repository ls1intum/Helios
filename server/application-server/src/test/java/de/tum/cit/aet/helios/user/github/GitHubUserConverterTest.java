package de.tum.cit.aet.helios.user.github;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GitHubUserConverterTest {

  private final GitHubUserConverter converter = new GitHubUserConverter();

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
}
