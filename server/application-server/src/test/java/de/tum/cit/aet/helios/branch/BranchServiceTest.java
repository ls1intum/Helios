package de.tum.cit.aet.helios.branch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.releasecandidate.ReleaseCandidateRepository;
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
public class BranchServiceTest {

  @InjectMocks private BranchService branchService;
  @Mock private BranchRepository branchRepository;
  @Mock private ReleaseCandidateRepository releaseCandidateRepository;
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

    final Branch b1 = new Branch();
    b1.setName("branch1");
    b1.setRepository(repo);

    final Branch b2 = new Branch();
    b2.setName("branch2");
    b2.setRepository(repo);

    final List<Branch> branches = List.of(b1, b2);

    final UserPreference userPreference = new UserPreference();
    userPreference.setFavouriteBranches(Set.of(b2));

    when(branchRepository.findAll()).thenReturn(branches);
    when(authService.getUserFromGithubId()).thenReturn(null);
    when(userPreferenceRepository.findByUser(null)).thenReturn(Optional.of(userPreference));

    BranchInfoDto b1Dto =
        BranchInfoDto.fromBranchAndUserPreference(b1, Optional.of(userPreference));
    BranchInfoDto b2Dto =
        BranchInfoDto.fromBranchAndUserPreference(b2, Optional.of(userPreference));

    assertEquals(2, branchService.getAllBranches().size());
    Assertions.assertIterableEquals(List.of(b2Dto, b1Dto), branchService.getAllBranches());
  }
}
