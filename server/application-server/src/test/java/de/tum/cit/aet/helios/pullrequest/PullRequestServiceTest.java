package de.tum.cit.aet.helios.pullrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository.Visibility;
import de.tum.cit.aet.helios.label.Label;
import de.tum.cit.aet.helios.pullrequest.pagination.PullRequestPageRequest;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.userpreference.UserPreference;
import de.tum.cit.aet.helios.userpreference.UserPreferenceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
public class PullRequestServiceTest {

  @InjectMocks private PullRequestService pullRequestService;
  @Mock private PullRequestRepository pullRequestsRepository;
  @Mock private UserPreferenceRepository userPreferenceRepository;
  @Mock private AuthService authService;

  @BeforeEach
  public void setUp() {
    RepositoryContext.setRepositoryId("1");
  }

  @AfterEach
  public void tearDown() {
    RepositoryContext.clear();
  }

  @Test
  public void testPinnedBranchesAreShownFirst() {
    final GitRepository repo = new GitRepository();
    repo.setRepositoryId(1L);

    final PullRequest pr1 = new PullRequest();
    pr1.setId(1L);
    pr1.setNumber(1);
    pr1.setRepository(repo);

    final PullRequest pr2 = new PullRequest();
    pr2.setId(2L);
    pr2.setNumber(2);
    pr2.setRepository(repo);

    final List<PullRequest> prs = List.of(pr1, pr2);

    final UserPreference userPreference = new UserPreference();
    userPreference.setFavouritePullRequests(Set.of(pr2));

    when(pullRequestsRepository.findByRepositoryRepositoryIdOrderByUpdatedAtDesc(1L))
        .thenReturn(prs);
    when(authService.isLoggedIn()).thenReturn(true);
    when(authService.getUserFromGithubId()).thenReturn(null);
    when(userPreferenceRepository.findByUser(null)).thenReturn(Optional.of(userPreference));

    PullRequestBaseInfoDto pr1Dto =
        PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr1, Optional.of(userPreference));

    PullRequestBaseInfoDto pr2Dto =
        PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr2, Optional.of(userPreference));

    assertEquals(2, pullRequestService.getAllPullRequests().size());
    assertIterableEquals(
        List.of(pr2Dto, pr1Dto), pullRequestService.getAllPullRequests());
  }

  @Test
  void getPaginatedPullRequestsUsesRequestedSort() {
    when(authService.isLoggedIn()).thenReturn(false);
    when(
        pullRequestsRepository.count(
            ArgumentMatchers.<Specification<PullRequest>>any()))
        .thenReturn(0L);
    when(
            pullRequestsRepository.findAll(
                ArgumentMatchers.<Specification<PullRequest>>any(),
                any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    PullRequestPageRequest request = new PullRequestPageRequest();
    request.setPage(1);
    request.setSize(20);
    request.setSortField("createdAt");
    request.setSortDirection("asc");

    pullRequestService.getPaginatedPullRequests(request);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(pullRequestsRepository)
        .findAll(ArgumentMatchers.<Specification<PullRequest>>any(), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();

    var actualOrder = pageable.getSort().iterator().next();
    assertEquals("createdAt", actualOrder.getProperty());
    assertEquals(Sort.Direction.ASC, actualOrder.getDirection());
    assertEquals(1, pageable.getSort().stream().count());
  }

  @Test
  void getPaginatedPullRequestsUsesUpdatedAtDescAsDefaultSort() {
    when(authService.isLoggedIn()).thenReturn(false);
    when(pullRequestsRepository.count(ArgumentMatchers.<Specification<PullRequest>>any()))
        .thenReturn(0L);
    when(
            pullRequestsRepository.findAll(
                ArgumentMatchers.<Specification<PullRequest>>any(),
                any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    PullRequestPageRequest request = new PullRequestPageRequest();
    request.setPage(1);
    request.setSize(20);

    pullRequestService.getPaginatedPullRequests(request);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(pullRequestsRepository, atLeastOnce())
        .findAll(ArgumentMatchers.<Specification<PullRequest>>any(), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();

    var actualOrder = pageable.getSort().stream().iterator().next();
    assertEquals("updatedAt", actualOrder.getProperty());
    assertEquals(org.springframework.data.domain.Sort.Direction.DESC, actualOrder.getDirection());
    assertEquals(1, pageable.getSort().stream().count());
  }

  @Test
  void getPullRequestFilterOptionsByRepositoryIdReturnsDistinctSortedValues() {
    final long repositoryId = 12L;

    User authorB = new User();
    authorB.setId(2L);
    authorB.setLogin("beta");
    authorB.setAvatarUrl("https://example.com/beta.png");
    authorB.setName("Beta");
    authorB.setHtmlUrl("https://github.com/beta");

    User authorA = new User();
    authorA.setId(1L);
    authorA.setLogin("alpha");
    authorA.setAvatarUrl("https://example.com/alpha.png");
    authorA.setName("Alpha");
    authorA.setHtmlUrl("https://github.com/alpha");

    User assignee = new User();
    assignee.setId(3L);
    assignee.setLogin("gamma");
    assignee.setAvatarUrl("https://example.com/gamma.png");
    assignee.setName("Gamma");
    assignee.setHtmlUrl("https://github.com/gamma");

    User reviewer = new User();
    reviewer.setId(4L);
    reviewer.setLogin("delta");
    reviewer.setAvatarUrl("https://example.com/delta.png");
    reviewer.setName("Delta");
    reviewer.setHtmlUrl("https://github.com/delta");

    Label labelB = new Label();
    labelB.setId(2L);
    labelB.setName("backend");
    labelB.setColor("ff0000");

    Label labelA = new Label();
    labelA.setId(1L);
    labelA.setName("api");
    labelA.setColor("00ff00");

    GitRepository repository = new GitRepository();
    repository.setRepositoryId(repositoryId);
    repository.setName("repo");
    repository.setNameWithOwner("owner/repo");
    repository.setHtmlUrl("https://github.com/owner/repo");
    repository.setUpdatedAt(OffsetDateTime.now());
    repository.setVisibility(Visibility.PUBLIC);
    labelA.setRepository(repository);
    labelB.setRepository(repository);

    when(pullRequestsRepository.findDistinctAuthorsByRepositoryId(repositoryId))
        .thenReturn(List.of(authorB, authorA));
    when(pullRequestsRepository.findDistinctAssigneesByRepositoryId(repositoryId))
        .thenReturn(List.of(assignee));
    when(pullRequestsRepository.findDistinctReviewersByRepositoryId(repositoryId))
        .thenReturn(List.of(reviewer));
    when(pullRequestsRepository.findDistinctLabelsByRepositoryId(repositoryId))
        .thenReturn(List.of(labelB, labelA));

    PullRequestFilterOptionsDto dto =
        pullRequestService.getPullRequestFilterOptionsByRepositoryId(repositoryId);

    assertIterableEquals(
        List.of("alpha", "beta"),
        dto.authors().stream().map(PullRequestFilterUserOptionDto::login).toList());
    assertIterableEquals(
        List.of("gamma"),
        dto.assignees().stream().map(PullRequestFilterUserOptionDto::login).toList());
    assertIterableEquals(
        List.of("delta"),
        dto.reviewers().stream().map(PullRequestFilterUserOptionDto::login).toList());
    assertIterableEquals(
        List.of("api", "backend"),
        dto.labels().stream().map(PullRequestFilterLabelOptionDto::name).toList());
  }
}
