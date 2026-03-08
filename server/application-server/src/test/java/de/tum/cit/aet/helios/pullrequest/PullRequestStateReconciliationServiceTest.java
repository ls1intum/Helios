package de.tum.cit.aet.helios.pullrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PullRequestStateReconciliationServiceTest {

  @Mock private PullRequestRepository pullRequestRepository;

  @Mock private GitRepoRepository gitRepoRepository;

  @Mock private GitHubFacade gitHubFacade;

  @Mock private GitHubPullRequestSyncService pullRequestSyncService;

  @Mock private GHRepository ghRepository;

  @InjectMocks
  private PullRequestStateReconciliationService pullRequestStateReconciliationService;

  @Test
  void reconcilePullRequestStateUpdatesClosedPullRequest() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("ls1intum/Helios");

    PullRequest pullRequest = new PullRequest();
    pullRequest.setId(1001L);
    pullRequest.setNumber(42);
    pullRequest.setRepository(repository);

    GHPullRequest gitHubPullRequest = org.mockito.Mockito.mock(GHPullRequest.class);

    when(gitRepoRepository.findByRepositoryId(1L)).thenReturn(Optional.of(repository));
    when(gitHubFacade.getRepository("ls1intum/Helios")).thenReturn(ghRepository);
    when(pullRequestRepository.findByRepositoryRepositoryIdAndStateOrderByUpdatedAtDesc(
            1L, Issue.State.OPEN))
        .thenReturn(List.of(pullRequest));
    when(ghRepository.getPullRequest(42)).thenReturn(gitHubPullRequest);
    when(gitHubPullRequest.getState()).thenReturn(GHIssueState.CLOSED);

    PullRequestStateReconciliationResultDto result =
        pullRequestStateReconciliationService.reconcilePullRequestState(1L, false);

    assertEquals(1, result.updatedCount());
    assertEquals(List.of(1001L), result.updatedPullRequestIds());
    assertEquals(List.of(42), result.updatedPullRequestNumbers());
    verify(pullRequestSyncService).processPullRequest(gitHubPullRequest);
  }

  @Test
  void reconcilePullRequestStateDryRunKeepsSyncSideEffectsDisabled() throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("ls1intum/Helios");

    PullRequest pullRequest = new PullRequest();
    pullRequest.setId(1001L);
    pullRequest.setNumber(42);
    pullRequest.setRepository(repository);

    GHPullRequest gitHubPullRequest = org.mockito.Mockito.mock(GHPullRequest.class);

    when(gitRepoRepository.findByRepositoryId(1L)).thenReturn(Optional.of(repository));
    when(gitHubFacade.getRepository("ls1intum/Helios")).thenReturn(ghRepository);
    when(pullRequestRepository.findByRepositoryRepositoryIdAndStateOrderByUpdatedAtDesc(
            1L, Issue.State.OPEN))
        .thenReturn(List.of(pullRequest));
    when(ghRepository.getPullRequest(42)).thenReturn(gitHubPullRequest);
    when(gitHubPullRequest.getState()).thenReturn(GHIssueState.CLOSED);

    PullRequestStateReconciliationResultDto result =
        pullRequestStateReconciliationService.reconcilePullRequestState(1L, true);

    assertTrue(result.dryRun());
    assertEquals(1, result.updatedCount());
    verify(pullRequestSyncService, never()).processPullRequest(gitHubPullRequest);
  }

  @Test
  void reconcilePullRequestStateMarksMissingPullRequestWhenGitHubReturnsNotFound()
      throws Exception {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);
    repository.setNameWithOwner("ls1intum/Helios");

    PullRequest pullRequest = new PullRequest();
    pullRequest.setId(1001L);
    pullRequest.setNumber(42);
    pullRequest.setRepository(repository);

    when(gitRepoRepository.findByRepositoryId(1L)).thenReturn(Optional.of(repository));
    when(gitHubFacade.getRepository("ls1intum/Helios")).thenReturn(ghRepository);
    when(pullRequestRepository.findByRepositoryRepositoryIdAndStateOrderByUpdatedAtDesc(
            1L, Issue.State.OPEN))
        .thenReturn(List.of(pullRequest));
    when(ghRepository.getPullRequest(42)).thenThrow(new IOException("Not Found"));

    PullRequestStateReconciliationResultDto result =
        pullRequestStateReconciliationService.reconcilePullRequestState(1L, false);

    assertEquals(1, result.missingCount());
    assertEquals(0, result.errorCount());
  }
}
