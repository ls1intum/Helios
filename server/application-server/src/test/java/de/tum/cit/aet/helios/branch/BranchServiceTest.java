package de.tum.cit.aet.helios.branch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.userpreference.UserPreference;
import de.tum.cit.aet.helios.userpreference.UserPreferenceRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

@RunWith(MockitoJUnitRunner.class)
@ExtendWith(MockitoExtension.class)
public class BranchServiceTest {

  @InjectMocks private BranchService branchService;
  @Mock private BranchRepository branchRepository;
  @Mock private ReleaseCandidateRepository releaseCandidateRepository;
  @Mock private UserPreferenceRepository userPreferenceRepository;
  @Mock private CommitRepository commitRepository;
  @Mock private AuthService authService;

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

    when(commitRepository.findByShaAndRepository(any(), any())).thenReturn(Optional.empty());

    when(branchRepository.findAll()).thenReturn(branches);
    when(authService.isLoggedIn()).thenReturn(true);
    when(authService.getUserFromGithubId()).thenReturn(null);
    when(userPreferenceRepository.findByUser(null)).thenReturn(Optional.of(userPreference));

    List<BranchInfoDto> allBranches = branchService.getAllBranches();

    BranchInfoDto b1Dto =
        BranchInfoDto.fromBranchAndUserPreference(b1, Optional.of(userPreference), commitRepository);
    BranchInfoDto b2Dto =
        BranchInfoDto.fromBranchAndUserPreference(b2, Optional.of(userPreference), commitRepository);

    assertEquals(2, allBranches.size());
    Assertions.assertIterableEquals(List.of(b2Dto, b1Dto), allBranches);
  }
}
