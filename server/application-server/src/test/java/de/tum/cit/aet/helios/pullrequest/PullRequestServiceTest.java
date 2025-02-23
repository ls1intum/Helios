package de.tum.cit.aet.helios.pullrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.userpreference.UserPreference;
import de.tum.cit.aet.helios.userpreference.UserPreferenceRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PullRequestServiceTest {

  @InjectMocks private PullRequestService pullRequestService;
  @Mock private PullRequestRepository pullRequestsRepository;
  @Mock private UserPreferenceRepository userPreferenceRepository;
  @Mock private AuthService authService;

  @BeforeEach
  public void init() {
    MockitoAnnotations.openMocks(this);
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

    when(pullRequestsRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(prs);
    when(authService.getUserFromGithubId()).thenReturn(null);
    when(userPreferenceRepository.findByUser(null)).thenReturn(Optional.of(userPreference));

    PullRequestBaseInfoDto pr1Dto =
        PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr1, Optional.of(userPreference));

    PullRequestBaseInfoDto pr2Dto =
        PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr2, Optional.of(userPreference));

    assertEquals(2, pullRequestService.getAllPullRequests().size());
    Assertions.assertIterableEquals(
        List.of(pr2Dto, pr1Dto), pullRequestService.getAllPullRequests());
  }
}
