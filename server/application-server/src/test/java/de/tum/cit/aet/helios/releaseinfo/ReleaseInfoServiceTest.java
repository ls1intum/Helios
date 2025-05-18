package de.tum.cit.aet.helios.releaseinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.sync.GitHubDataSyncOrchestrator;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.releaseinfo.release.ReleaseRepository;
import de.tum.cit.aet.helios.releaseinfo.release.github.GitHubReleaseSyncService;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidate;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateCreateDto;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateEvaluationRepository;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateException;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReleaseInfoServiceTest {

  @Mock private GitHubService gitHubService;
  @Mock private GitRepoRepository gitRepoRepository;
  @Mock private ReleaseCandidateRepository releaseCandidateRepository;
  @Mock private ReleaseRepository releaseRepository;
  @Mock private CommitRepository commitRepository;
  @Mock private DeploymentRepository deploymentRepository;
  @Mock private BranchRepository branchRepository;
  @Mock private UserRepository userRepository;
  @Mock private ReleaseCandidateEvaluationRepository releaseCandidateEvaluationRepository;
  @Mock private AuthService authService;
  @Mock private HeliosDeploymentRepository heliosDeploymentRepository;
  @Mock private GitHubDataSyncOrchestrator gitHubDataSyncOrchestrator;
  @Mock private GitHubReleaseSyncService gitHubReleaseSyncService;

  @InjectMocks private ReleaseInfoService releaseInfoService;

  private GitRepository repository;
  private User user;
  private Commit commit;
  private Branch branch;
  private ReleaseCandidate releaseCandidate;

  private Object getField(Object obj, String fieldName) throws Exception {
    if (obj == null) {
      return null;
    }
    Field field = obj.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(obj);
  }

  @BeforeEach
  void setUp() {
    // Setup test data
    repository = new GitRepository();
    ReflectionTestUtils.setField(repository, "repositoryId", 1L);
    ReflectionTestUtils.setField(repository, "name", "test-repo");
    ReflectionTestUtils.setField(repository, "nameWithOwner", "test/test-repo");

    user = new User();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(user, "login", "test-user");

    commit = new Commit();
    ReflectionTestUtils.setField(commit, "sha", "abc123");
    ReflectionTestUtils.setField(commit, "repository", repository);

    branch = new Branch();
    ReflectionTestUtils.setField(branch, "name", "main");
    ReflectionTestUtils.setField(branch, "repository", repository);
    ReflectionTestUtils.setField(branch, "commitSha", "abc123");

    releaseCandidate = new ReleaseCandidate();
    ReflectionTestUtils.setField(releaseCandidate, "id", 1L);
    ReflectionTestUtils.setField(releaseCandidate, "name", "v1.0.0");
    ReflectionTestUtils.setField(releaseCandidate, "repository", repository);
    ReflectionTestUtils.setField(releaseCandidate, "commit", commit);
    ReflectionTestUtils.setField(releaseCandidate, "branch", branch);
    ReflectionTestUtils.setField(releaseCandidate, "createdBy", user);
    ReflectionTestUtils.setField(releaseCandidate, "createdAt", OffsetDateTime.now());
  }

  @Test
  void testGetAllReleaseInfos() throws Exception {
    when(releaseCandidateRepository.findAllByOrderByCreatedAtDesc())
        .thenReturn(List.of(releaseCandidate));

    List<ReleaseInfoListDto> result = releaseInfoService.getAllReleaseInfos();

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(getField(releaseCandidate, "name"), result.get(0).name());
    verify(releaseCandidateRepository).findAllByOrderByCreatedAtDesc();
  }

  @Test
  void testGetReleaseInfoByName() throws Exception {
    try (MockedStatic<RepositoryContext> mockedStatic = mockStatic(RepositoryContext.class)) {
      mockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(1L);
      when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(1L, "v1.0.0"))
          .thenReturn(Optional.of(releaseCandidate));
      when(heliosDeploymentRepository.findByRepositoryIdAndSha(1L, "abc123")).thenReturn(List.of());
      when(deploymentRepository.findByRepositoryRepositoryIdAndSha(1L, "abc123"))
          .thenReturn(List.of());

      ReleaseInfoDetailsDto result = releaseInfoService.getReleaseInfoByName("v1.0.0");

      assertNotNull(result);
      assertEquals(getField(releaseCandidate, "name"), result.name());
      Commit commit = (Commit) getField(releaseCandidate, "commit");
      assertEquals(getField(commit, "sha"), result.commit().sha());
      Branch branch = (Branch) getField(releaseCandidate, "branch");
      assertEquals(getField(branch, "name"), result.branch().name());
      assertTrue(result.deployments().isEmpty());
      assertTrue(result.evaluations().isEmpty());
      assertNull(result.release());
      User createdBy = (User) getField(releaseCandidate, "createdBy");
      assertEquals(getField(createdBy, "login"), result.createdBy().login());
      assertEquals(getField(releaseCandidate, "createdAt"), result.createdAt());
    }
  }

  @Test
  void testGetReleaseInfoByNameNotFound() {
    try (MockedStatic<RepositoryContext> mockedStatic = mockStatic(RepositoryContext.class)) {
      mockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(1L);
      when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(1L, "v1.0.0"))
          .thenReturn(Optional.empty());

      assertThrows(
          ReleaseCandidateException.class, () -> releaseInfoService.getReleaseInfoByName("v1.0.0"));
    }
  }

  @Test
  void testCreateReleaseCandidate() throws Exception {
    try (MockedStatic<RepositoryContext> mockedStatic = mockStatic(RepositoryContext.class)) {
      mockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(1L);
      when(authService.getPreferredUsername()).thenReturn("test-user");
      when(releaseCandidateRepository.existsByRepositoryRepositoryIdAndName(1L, "v1.0.0"))
          .thenReturn(false);
      when(commitRepository.findByShaAndRepositoryRepositoryId("abc123", 1L))
          .thenReturn(Optional.of(commit));
      when(branchRepository.findByNameAndRepositoryRepositoryId("main", 1L))
          .thenReturn(Optional.of(branch));
      when(gitRepoRepository.findById(1L)).thenReturn(Optional.of(repository));
      when(userRepository.findByLoginIgnoreCase("test-user")).thenReturn(Optional.of(user));
      when(releaseCandidateRepository.save(any(ReleaseCandidate.class)))
          .thenReturn(releaseCandidate);

      ReleaseInfoListDto result =
          releaseInfoService.createReleaseCandidate(
              new ReleaseCandidateCreateDto("v1.0.0", "abc123", "main"));

      assertNotNull(result);
      assertEquals(getField(releaseCandidate, "name"), result.name());
      Commit commit = (Commit) getField(releaseCandidate, "commit");
      assertEquals(getField(commit, "sha"), result.commitSha());
      Branch branch = (Branch) getField(releaseCandidate, "branch");
      assertEquals(getField(branch, "name"), result.branchName());
      assertFalse(result.isPublished());
    }
  }

  @Test
  void testCreateReleaseCandidateAlreadyExists() {
    try (MockedStatic<RepositoryContext> mockedStatic = mockStatic(RepositoryContext.class)) {
      mockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(1L);
      when(releaseCandidateRepository.existsByRepositoryRepositoryIdAndName(1L, "v1.0.0"))
          .thenReturn(true);

      assertThrows(
          ReleaseCandidateException.class,
          () ->
              releaseInfoService.createReleaseCandidate(
                  new ReleaseCandidateCreateDto("v1.0.0", "abc123", "main")));
    }
  }
}
