package de.tum.cit.aet.helios.github.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubDataSyncOrchestratorTest {

  @Mock private GitHubPullRequestSyncService pullRequestSyncService;

  @Test
  void syncPullRequestsOfRepositoryBuildsOpenPullRequestQuery() {
    final GitHubDataSyncOrchestrator orchestrator =
        new GitHubDataSyncOrchestrator(
            null,
            pullRequestSyncService,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    GHRepository repository = mock(GHRepository.class);
    GHPullRequestQueryBuilder builder = mock(GHPullRequestQueryBuilder.class);
    @SuppressWarnings("unchecked")
    PagedIterable<GHPullRequest> iterable = mock(PagedIterable.class);
    @SuppressWarnings("unchecked")
    PagedIterator<GHPullRequest> iterator = mock(PagedIterator.class);

    when(repository.queryPullRequests()).thenReturn(builder);
    when(builder.state(any())).thenReturn(builder);
    when(builder.sort(any())).thenReturn(builder);
    when(builder.direction(any())).thenReturn(builder);
    when(builder.list()).thenReturn(iterable);
    when(iterable.withPageSize(100)).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(iterator);
    lenient().when(iterator.hasNext()).thenReturn(false);

    orchestrator.syncPullRequestsOfRepository(repository);

    verify(builder).state(GHIssueState.OPEN);
    verify(builder).sort(GHPullRequestQueryBuilder.Sort.UPDATED);
    verify(builder).direction(GHDirection.DESC);
  }
}
