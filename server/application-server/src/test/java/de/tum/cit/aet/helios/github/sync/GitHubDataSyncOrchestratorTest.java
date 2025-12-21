package de.tum.cit.aet.helios.github.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedIterable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubDataSyncOrchestratorTest {

  @Mock private GitHubPullRequestSyncService pullRequestSyncService;

  private GitHubDataSyncOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator =
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
  }

  @Test
  void syncPullRequestsWithCutoffStopsWhenOlderThanCutoff() throws Exception {
    GHPullRequest first = mockPullRequest(101L, OffsetDateTime.now().minusHours(1));
    GHPullRequest second = mockPullRequest(102L, OffsetDateTime.now().minusDays(1));
    GHPullRequest third = mockPullRequest(103L, OffsetDateTime.now().minusDays(10));
    GHRepository repository = mockRepositoryForPullRequests(List.of(first, second, third));

    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(3);
    orchestrator.syncPullRequestsOfRepository(repository, Optional.of(cutoff));

    verify(pullRequestSyncService).processPullRequest(first);
    verify(pullRequestSyncService).processPullRequest(second);
    verify(pullRequestSyncService, never()).processPullRequest(third);
  }

  @Test
  void syncPullRequestsWithoutCutoffProcessesAll() throws Exception {
    GHPullRequest first = mockPullRequest(201L, OffsetDateTime.now().minusHours(2));
    GHPullRequest second = mockPullRequest(202L, OffsetDateTime.now().minusDays(2));
    GHRepository repository = mockRepositoryForPullRequests(List.of(first, second));

    orchestrator.syncPullRequestsOfRepository(repository, Optional.empty());

    verify(pullRequestSyncService).processPullRequest(first);
    verify(pullRequestSyncService).processPullRequest(second);
  }

  private GHRepository mockRepositoryForPullRequests(List<GHPullRequest> pullRequests) {
    GHRepository repository = mock(GHRepository.class);
    GHPullRequestQueryBuilder builder = mock(GHPullRequestQueryBuilder.class);
    @SuppressWarnings("unchecked")
    PagedIterable<GHPullRequest> iterable = mock(PagedIterable.class);
    PagedIterator<GHPullRequest> pagedIterator = mockPagedIterator(pullRequests);

    when(repository.queryPullRequests()).thenReturn(builder);
    when(builder.state(any())).thenReturn(builder);
    when(builder.sort(any())).thenReturn(builder);
    when(builder.direction(any())).thenReturn(builder);
    when(builder.list()).thenReturn(iterable);
    when(iterable.withPageSize(100)).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(pagedIterator);

    return repository;
  }

  private PagedIterator<GHPullRequest> mockPagedIterator(List<GHPullRequest> pullRequests) {
    PagedIterator<GHPullRequest> iterator = mock(PagedIterator.class);
    final int[] index = {0};
    when(iterator.hasNext())
        .thenAnswer(invocation -> index[0] < pullRequests.size());
    when(iterator.next())
        .thenAnswer(invocation -> pullRequests.get(index[0]++));
    return iterator;
  }

  private GHPullRequest mockPullRequest(Long id, OffsetDateTime updatedAt) throws Exception {
    GHPullRequest pullRequest = mock(GHPullRequest.class);
    lenient().doReturn(Date.from(updatedAt.toInstant())).when(pullRequest).getUpdatedAt();
    return pullRequest;
  }
}
