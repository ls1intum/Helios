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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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

  @InjectMocks
  private BranchService branchService;
  @Mock
  private BranchRepository branchRepository;
  @Mock
  private ReleaseCandidateRepository releaseCandidateRepository;
  @Mock
  private UserPreferenceRepository userPreferenceRepository;
  @Mock
  private CommitRepository commitRepository;
  @Mock
  private AuthService authService;

  private Branch b1;
  private Branch b2;
  private Branch b3;

  @BeforeEach
  public void setUp() {
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(1L);

    b1 = new Branch();
    b1.setName("alpha");
    b1.setRepository(repo);
    b1.setUpdatedAt(OffsetDateTime.parse("2025-04-01T10:00:00Z"));

    b2 = new Branch();
    b2.setName("beta");
    b2.setRepository(repo);
    b2.setUpdatedAt(OffsetDateTime.parse("2025-03-01T10:00:00Z"));

    b3 = new Branch();
    b3.setName("gamma");
    b3.setRepository(repo);
    b3.setUpdatedAt(OffsetDateTime.parse("2025-03-15T10:00:00Z"));

    when(commitRepository.findByShaAndRepository(any(), any())).thenReturn(Optional.empty());
    when(branchRepository.findAll()).thenReturn(List.of(b1, b2, b3));
    when(authService.isLoggedIn()).thenReturn(false);
  }

  @Test
  public void testPinnedBranchesAreShownFirst() {
    final UserPreference userPreference = new UserPreference();
    userPreference.setFavouriteBranches(Set.of(b2));

    when(authService.isLoggedIn()).thenReturn(true);
    when(authService.getUserFromGithubId()).thenReturn(null);
    when(userPreferenceRepository.findByUser(null)).thenReturn(Optional.of(userPreference));

    List<BranchInfoDto> allBranches = branchService.getAllBranches("updatedAt", "desc");

    BranchInfoDto b1Dto =
        BranchInfoDto.fromBranchAndUserPreference(b1, Optional.of(userPreference),
            commitRepository);
    BranchInfoDto b2Dto =
        BranchInfoDto.fromBranchAndUserPreference(b2, Optional.of(userPreference),
            commitRepository);
    BranchInfoDto b3Dto =
        BranchInfoDto.fromBranchAndUserPreference(b3, Optional.of(userPreference),
            commitRepository);

    assertEquals(3, allBranches.size());
    Assertions.assertIterableEquals(List.of(b2Dto, b1Dto, b3Dto), allBranches);
  }

  @Test
  public void testSortByUpdatedAtDescending() {
    List<BranchInfoDto> result = branchService.getAllBranches("updatedAt", "desc");

    assertEquals(3, result.size());
    assertEquals("alpha", result.get(0).name(), "Most recently updated branch should be first");
    assertEquals("gamma", result.get(1).name());
    assertEquals("beta", result.get(2).name(), "Oldest updated branch should be last");
  }

  @Test
  public void testSortByUpdatedAtAscending() {
    List<BranchInfoDto> result = branchService.getAllBranches("updatedAt", "asc");

    assertEquals(3, result.size());
    assertEquals("beta", result.get(0).name(), "Oldest updated branch should be first");
    assertEquals("gamma", result.get(1).name());
    assertEquals("alpha", result.get(2).name(), "Most recently updated branch should be last");
  }

  @Test
  public void testSortByNameAscending() {
    List<BranchInfoDto> result = branchService.getAllBranches("name", "asc");

    assertEquals(3, result.size());
    assertEquals("alpha", result.get(0).name());
    assertEquals("beta", result.get(1).name());
    assertEquals("gamma", result.get(2).name());
  }

  @Test
  public void testSortByNameDescending() {
    List<BranchInfoDto> result = branchService.getAllBranches("name", "desc");

    assertEquals(3, result.size());
    assertEquals("gamma", result.get(0).name());
    assertEquals("beta", result.get(1).name());
    assertEquals("alpha", result.get(2).name());
  }
}
