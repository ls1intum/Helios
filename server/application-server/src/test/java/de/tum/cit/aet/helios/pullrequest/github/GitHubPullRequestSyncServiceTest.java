package de.tum.cit.aet.helios.pullrequest.github;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubPullRequestSyncServiceTest {

  @Test
  void isOfflineErrorReturnsTrueWhenMessageMatches() {
    IOException exception = new IOException("Offline");

    assertTrue(GitHubPullRequestSyncService.isOfflineError(exception));
  }

  @Test
  void isOfflineErrorReturnsFalseForOtherMessages() {
    IOException exception = new IOException("Bad credentials");

    assertFalse(GitHubPullRequestSyncService.isOfflineError(exception));
  }
}
