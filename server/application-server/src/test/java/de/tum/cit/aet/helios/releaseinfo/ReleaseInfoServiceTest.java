package de.tum.cit.aet.helios.releaseinfo;


import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.sync.GitHubDataSyncOrchestrator;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.releaseinfo.release.Release;
import de.tum.cit.aet.helios.releaseinfo.release.ReleaseRepository;
import de.tum.cit.aet.helios.releaseinfo.release.github.GitHubReleaseSyncService;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.CommitsSinceReleaseCandidateDto;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidate;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateCreateDto;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateEvaluation;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateEvaluationRepository;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateException;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseInfoServiceTest {

  @Mock
  private GitHubService gitHubService;
  @Mock
  private GitRepoRepository gitRepoRepository;
  @Mock
  private ReleaseCandidateRepository releaseCandidateRepository;
  @Mock
  private ReleaseRepository releaseRepository;
  @Mock
  private CommitRepository commitRepository;
  @Mock
  private DeploymentRepository deploymentRepository;
  @Mock
  private BranchRepository branchRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private ReleaseCandidateEvaluationRepository releaseCandidateEvaluationRepository;
  @Mock
  private AuthService authService;
  @Mock
  private HeliosDeploymentRepository heliosDeploymentRepository;
  @Mock
  private GitHubDataSyncOrchestrator gitHubDataSyncOrchestrator;
  @Mock
  private GitHubReleaseSyncService gitHubReleaseSyncService;

  @InjectMocks
  private ReleaseInfoService service;

  private final Long repoId = 1L;

  private ReleaseCandidate candidate;
  private GitRepository repo;
  private Commit commit;
  private Environment envA;
  private Environment envB;

  @BeforeEach
  void setUp() {
    // Ensure RepositoryContext is set to the test repository ID
    RepositoryContext.setRepositoryId(repoId.toString());

    // Construct a ReleaseCandidate with a GitRepository and Commit
    repo = new GitRepository();
    repo.setRepositoryId(repoId);
    commit = new Commit();
    commit.setSha("commit-sha");
    candidate = new ReleaseCandidate();
    candidate.setRepository(repo);
    candidate.setCommit(commit);

    // Two environments with distinct IDs
    envA = new Environment();
    envA.setId(100L);
    envB = new Environment();
    envB.setId(200L);
  }

  @AfterEach
  void tearDown() {
    RepositoryContext.clear();
  }

  @Test
  void getCommitsFromBranchSinceLastReleaseCandidate_repositoryNotFound_throwsException() {
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.empty());

    // Act & Assert
    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class,
            () -> service.getCommitsFromBranchSinceLastReleaseCandidate("anyBranch"));
    assertTrue(ex.getMessage().contains("Repository not found"));
  }

  @Test
  void getCommitsFromBranchSinceLastReleaseCandidate_branchNotFound_throwsException()
      throws IOException {
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    repo.setNameWithOwner("owner/repo");
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));

    // Simulate GitHubService returning a non-null GHRepository
    GHRepository ghRepo = mock(GHRepository.class);
    when(gitHubService.getRepository("owner/repo")).thenReturn(ghRepo);

    // Branch lookup returns empty
    when(branchRepository.findByRepositoryRepositoryIdAndName(repoId, "missing"))
        .thenReturn(Optional.empty());

    // Act & Assert
    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class,
            () -> service.getCommitsFromBranchSinceLastReleaseCandidate("missing"));
    assertTrue(ex.getMessage().contains("Branch not found"));
  }

  @Test
  void getCommitsFromBranchSinceLastReleaseCandidate_noLastRc_returnsEmptyDto() throws IOException {
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    repo.setNameWithOwner("owner/repo");
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));

    GHRepository ghRepo = mock(GHRepository.class);
    when(gitHubService.getRepository("owner/repo")).thenReturn(ghRepo);

    Branch branch = new Branch();
    branch.setCommitSha("branch-sha");
    when(branchRepository.findByRepositoryRepositoryIdAndName(repoId, "main"))
        .thenReturn(Optional.of(branch));

    when(releaseCandidateRepository.findByRepository(repo)).thenReturn(List.of());

    // Act
    CommitsSinceReleaseCandidateDto dto =
        service.getCommitsFromBranchSinceLastReleaseCandidate("main");

    // Assert
    assertEquals(-1, dto.aheadBy());
    assertEquals(-1, dto.behindBy());
    assertTrue(dto.commits().isEmpty());
    assertNull(dto.compareUrl());
  }

  @Test
  void getCommitsFromBranchSinceLastReleaseCandidate_compareThrowsIoException_throwsException()
      throws IOException {
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    repo.setNameWithOwner("owner/repo");
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));

    GHRepository ghRepo = mock(GHRepository.class);
    when(gitHubService.getRepository("owner/repo")).thenReturn(ghRepo);

    Branch branch = new Branch();
    branch.setCommitSha("branch-sha");
    when(branchRepository.findByRepositoryRepositoryIdAndName(repoId, "main"))
        .thenReturn(Optional.of(branch));

    Commit lastRcCommit = new Commit();
    lastRcCommit.setSha("rc-sha");
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setCommit(lastRcCommit);
    when(releaseCandidateRepository.findByRepository(repo)).thenReturn(List.of(rc));

    // Make getCompare(...) throw IOException
    when(ghRepo.getCompare("rc-sha", "branch-sha")).thenThrow(new IOException("GH error"));

    // Act & Assert
    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class,
            () -> service.getCommitsFromBranchSinceLastReleaseCandidate("main"));
    assertTrue(ex.getMessage().contains("Failed to fetch compare commit data from GitHub"));
  }

  @Test
  void getCommitsFromBranchSinceLastReleaseCandidate_successfulCompare_returnsDto()
      throws IOException {
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    repo.setNameWithOwner("owner/repo");
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));

    GHRepository ghRepo = mock(GHRepository.class);
    when(gitHubService.getRepository("owner/repo")).thenReturn(ghRepo);

    Branch branch = new Branch();
    branch.setCommitSha("branch-sha");
    when(branchRepository.findByRepositoryRepositoryIdAndName(repoId, "main"))
        .thenReturn(Optional.of(branch));

    Commit lastRcCommit = new Commit();
    lastRcCommit.setSha("rc-sha");
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setCommit(lastRcCommit);
    when(releaseCandidateRepository.findByRepository(repo)).thenReturn(List.of(rc));

    // Mock GHCompare and its contents
    GHCompare compare = mock(GHCompare.class);
    when(ghRepo.getCompare("rc-sha", "branch-sha")).thenReturn(compare);
    when(compare.getAheadBy()).thenReturn(5);
    when(compare.getBehindBy()).thenReturn(2);

    // Build a mock GHCompare.Commit with deep stubs so getCommit() can be chained
    GHCompare.Commit ghCommit = mock(GHCompare.Commit.class, RETURNS_DEEP_STUBS);
    when(ghCommit.getCommit().getMessage()).thenReturn("commit-msg");
    when(ghCommit.getCommit().getCommitter().getName()).thenReturn("committer");
    when(ghCommit.getCommit().getCommitter().getEmail()).thenReturn("committer@example.com");
    when(ghCommit.getSHA1()).thenReturn("branch-sha");
    when(ghCommit.getHtmlUrl()).thenReturn(new java.net.URL("http://example.com/c/sha"));

    // Stub getCommits() to return an array
    when(compare.getCommits()).thenReturn(new GHCompare.Commit[] {ghCommit});
    when(compare.getHtmlUrl()).thenReturn(new java.net.URL("http://example.com/compare"));

    // Act
    CommitsSinceReleaseCandidateDto dto =
        service.getCommitsFromBranchSinceLastReleaseCandidate("main");

    // Assert
    assertEquals(5, dto.aheadBy());
    assertEquals(2, dto.behindBy());
    assertEquals(1, dto.commits().size());
    var info = dto.commits().getFirst();
    assertEquals("branch-sha", info.sha());
    assertEquals("commit-msg", info.message());
    assertEquals("committer", info.authorName());
    assertEquals("committer@example.com", info.authorEmail());
    assertTrue(dto.compareUrl().contains("compare"));
  }


  @SuppressWarnings("unchecked")
  private List<LatestDeploymentUnion> invokeGetCandidateDeployments() throws Exception {
    Method m =
        ReleaseInfoService.class.getDeclaredMethod("getCandidateDeployments",
            ReleaseCandidate.class);
    m.setAccessible(true);
    return (List<LatestDeploymentUnion>) m.invoke(service, candidate);
  }

  @Test
  void onlyHelios_returnsHeliosUnions() throws Exception {
    HeliosDeployment hd = new HeliosDeployment();
    hd.setId(10L);
    hd.setSha("commit-sha");
    hd.setEnvironment(envA);
    hd.setCreatedAt(OffsetDateTime.parse("2023-01-01T10:00:00Z"));
    when(heliosDeploymentRepository.findByRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of(hd));
    when(deploymentRepository.findByRepositoryRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of());

    // Act
    List<LatestDeploymentUnion> result = invokeGetCandidateDeployments();

    // Assert
    assertEquals(1, result.size());
    LatestDeploymentUnion union = result.getFirst();
    assertTrue(union.isHeliosDeployment());
    assertFalse(union.isRealDeployment());
    assertEquals(hd.getId(), union.getId());
    assertEquals(envA.getId(), union.getEnvironment().getId());
    assertEquals(hd.getCreatedAt(), union.getCreatedAt());
  }

  @Test
  void onlyReal_returnsRealUnions() throws Exception {
    Deployment dep = new Deployment();
    dep.setId(20L);
    dep.setSha("commit-sha");
    dep.setEnvironment(envB);
    dep.setCreatedAt(OffsetDateTime.parse("2023-02-01T12:00:00Z"));
    when(heliosDeploymentRepository.findByRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of());
    when(deploymentRepository.findByRepositoryRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of(dep));

    // Act
    List<LatestDeploymentUnion> result = invokeGetCandidateDeployments();

    // Assert
    assertEquals(1, result.size());
    LatestDeploymentUnion union = result.getFirst();
    assertTrue(union.isRealDeployment());
    assertFalse(union.isHeliosDeployment());
    assertEquals(dep.getId(), union.getId());
    assertEquals(envB.getId(), union.getEnvironment().getId());
    assertEquals(dep.getCreatedAt(), union.getCreatedAt());
  }

  @Test
  void realOverridesHelios_whenRealIsNewer() throws Exception {
    // Helios in envA at 2023-01-01
    HeliosDeployment hd = new HeliosDeployment();
    hd.setId(10L);
    hd.setSha("commit-sha");
    hd.setEnvironment(envA);
    hd.setCreatedAt(OffsetDateTime.parse("2023-01-01T10:00:00Z"));

    // Real in envA at 2023-01-02 (newer)
    Deployment depNewer = new Deployment();
    depNewer.setId(20L);
    depNewer.setSha("commit-sha");
    depNewer.setEnvironment(envA);
    depNewer.setCreatedAt(OffsetDateTime.parse("2023-01-02T10:00:00Z"));

    when(heliosDeploymentRepository.findByRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of(hd));
    when(deploymentRepository.findByRepositoryRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of(depNewer));

    // Act
    List<LatestDeploymentUnion> result = invokeGetCandidateDeployments();

    // Assert: should pick the real Deployment because it's newer
    assertEquals(1, result.size());
    LatestDeploymentUnion union = result.getFirst();
    assertTrue(union.isRealDeployment());
    assertEquals(depNewer.getId(), union.getId());
    assertEquals(envA.getId(), union.getEnvironment().getId());
    assertEquals(depNewer.getCreatedAt(), union.getCreatedAt());
  }

  @Test
  void heliosStays_whenRealIsOlder() throws Exception {
    // Helios in envA at 2023-01-02
    HeliosDeployment hd = new HeliosDeployment();
    hd.setId(10L);
    hd.setSha("commit-sha");
    hd.setEnvironment(envA);
    hd.setCreatedAt(OffsetDateTime.parse("2023-01-02T10:00:00Z"));

    // Real in envA at 2023-01-01 (older)
    Deployment depOlder = new Deployment();
    depOlder.setId(20L);
    depOlder.setSha("commit-sha");
    depOlder.setEnvironment(envA);
    depOlder.setCreatedAt(OffsetDateTime.parse("2023-01-01T10:00:00Z"));

    when(heliosDeploymentRepository.findByRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of(hd));
    when(deploymentRepository.findByRepositoryRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of(depOlder));

    // Act
    List<LatestDeploymentUnion> result = invokeGetCandidateDeployments();

    // Assert: should keep the HeliosDeployment because it's newer
    assertEquals(1, result.size());
    LatestDeploymentUnion union = result.getFirst();
    assertTrue(union.isHeliosDeployment());
    assertEquals(hd.getId(), union.getId());
    assertEquals(envA.getId(), union.getEnvironment().getId());
    assertEquals(hd.getCreatedAt(), union.getCreatedAt());
  }

  @Test
  void mergesMultipleEnvironments_correctly() throws Exception {
    // Helios in envA and envB
    HeliosDeployment hdA = new HeliosDeployment();
    hdA.setId(10L);
    hdA.setSha("commit-sha");
    hdA.setEnvironment(envA);
    hdA.setCreatedAt(OffsetDateTime.parse("2023-01-01T10:00:00Z"));

    HeliosDeployment hdB = new HeliosDeployment();
    hdB.setId(11L);
    hdB.setSha("commit-sha");
    hdB.setEnvironment(envB);
    hdB.setCreatedAt(OffsetDateTime.parse("2023-01-01T11:00:00Z"));

    // Real in envB only, newer than hdB
    Deployment depB = new Deployment();
    depB.setId(20L);
    depB.setSha("commit-sha");
    depB.setEnvironment(envB);
    depB.setCreatedAt(OffsetDateTime.parse("2023-01-02T09:00:00Z"));

    when(heliosDeploymentRepository.findByRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of(hdA, hdB));
    when(deploymentRepository.findByRepositoryRepositoryIdAndSha(repoId, "commit-sha"))
        .thenReturn(List.of(depB));

    // Act
    List<LatestDeploymentUnion> result = invokeGetCandidateDeployments();

    // Assert: envA should come from Helios, envB from real (because real is newer)
    assertEquals(2, result.size());
    boolean sawEnvAaHelios = false;
    boolean sawEnvBbReal = false;
    for (LatestDeploymentUnion union : result) {
      if (union.getEnvironment().getId().equals(envA.getId())) {
        assertTrue(union.isHeliosDeployment());
        assertEquals(hdA.getId(), union.getId());
        sawEnvAaHelios = true;
      }
      if (union.getEnvironment().getId().equals(envB.getId())) {
        assertTrue(union.isRealDeployment());
        assertEquals(depB.getId(), union.getId());
        sawEnvBbReal = true;
      }
    }
    assertTrue(sawEnvAaHelios, "Expected a HeliosDeployment union for envA");
    assertTrue(sawEnvBbReal, "Expected a real Deployment union for envB");
  }


  @Test
  void getAllReleaseInfos_returnsMappedList() {
    ReleaseCandidate rc1 = new ReleaseCandidate();
    rc1.setName("rc1");
    rc1.setCreatedAt(OffsetDateTime.now().minusDays(1));
    Commit commit1 = new Commit();
    commit1.setSha("dummySha1");
    rc1.setCommit(commit1);

    ReleaseCandidate rc2 = new ReleaseCandidate();
    rc2.setName("rc2");
    rc2.setCreatedAt(OffsetDateTime.now());
    Commit commit2 = new Commit();
    commit2.setSha("dummySha2");
    rc2.setCommit(commit2);

    when(releaseCandidateRepository.findAllByOrderByCreatedAtDesc())
        .thenReturn(List.of(rc2, rc1));

    // Act
    List<ReleaseInfoListDto> result = service.getAllReleaseInfos();

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("rc2", result.get(0).name());
    assertEquals("rc1", result.get(1).name());
  }

  @Test
  void getReleaseInfoByName_successfulMapping_returnsDetailsDto() {

    // --- GitRepository (for both candidate and commit/branch) ---
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);

    // --- Commit ---
    Commit commit = new Commit();
    commit.setSha("abc123");
    commit.setRepository(repo); // ensure CommitInfoDto.fromCommit() can access repository

    // --- Branch ---
    Branch branch = new Branch();
    branch.setName("main");
    branch.setCommitSha("abc123");
    branch.setRepository(repo); // ensure BranchInfoDto.fromBranch() can access repository

    // --- Creator (User) ---
    User creator = new User();
    creator.setLogin("testuser");
    creator.setId(42L);
    creator.setAvatarUrl("http://example.com/avatar");
    creator.setName("Test User");
    creator.setHtmlUrl("http://example.com/user");

    // --- Release (with body and GitHub URL) ---
    Release rel = new Release();
    rel.setDraft(false);
    rel.setPrerelease(false);
    rel.setBody("Release notes");
    rel.setGithubUrl("http://github.com/release/1.0");

    // --- One evaluation ---
    ReleaseCandidateEvaluation eval = new ReleaseCandidateEvaluation();
    eval.setEvaluatedBy(creator);
    eval.setWorking(true);
    eval.setComment("Looks good");

    // --- Candidate ---
    String candidateName = "release-1.0";
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(candidateName);
    rc.setCommit(commit);
    rc.setBranch(branch);
    rc.setCreatedBy(creator);
    rc.setCreatedAt(OffsetDateTime.parse("2023-01-01T12:00:00Z"));
    rc.setBody("Release body");
    rc.setRelease(rel);
    rc.getEvaluations().add(eval);
    rc.setRepository(repo); // attach the same GitRepository to the candidate

    // Stub repository lookup
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, candidateName))
        .thenReturn(Optional.of(rc));

    // Stub "no deployments" for that repo/sha
    when(heliosDeploymentRepository.findByRepositoryIdAndSha(repoId, "abc123"))
        .thenReturn(List.of());
    when(deploymentRepository.findByRepositoryRepositoryIdAndSha(repoId, "abc123"))
        .thenReturn(List.of());

    // Act
    ReleaseInfoDetailsDto details = service.getReleaseInfoByName(candidateName);

    // Assert
    Assertions.assertNotNull(details);
    assertEquals(candidateName, details.name());

    // CommitInfoDto
    assertEquals("abc123", details.commit().sha());

    // BranchInfoDto
    assertEquals("main", details.branch().name());

    // Deployments should be empty
    assertTrue(details.deployments().isEmpty());

    // Evaluations
    assertEquals(1, details.evaluations().size());
    var evalDto = details.evaluations().getFirst();
    assertTrue(evalDto.isWorking());
    assertEquals("Looks good", evalDto.comment());

    // ReleaseDto fields
    Assertions.assertNotNull(details.release());
    assertEquals("Release notes", details.release().body());
    assertEquals("http://github.com/release/1.0", details.release().githubUrl());

    // Creator UserInfoDto
    assertEquals(42L, details.createdBy().id());
    assertEquals("testuser", details.createdBy().login());

    // Body
    assertEquals("Release body", details.body());
  }


  @Test
  void getReleaseInfoByName_notFound_throwsException() {
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, "no-such"))
        .thenReturn(Optional.empty());

    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class,
            () -> service.getReleaseInfoByName("no-such"));
    assertTrue(ex.getMessage().contains("ReleaseCandidate not found"));
  }

  @Test
  void createReleaseCandidate_successfulCreation_returnsDto() {
    String commitSha = "abc123";
    Commit commit = new Commit();
    commit.setSha(commitSha);
    when(commitRepository.findByShaAndRepositoryRepositoryId(commitSha, repoId))
        .thenReturn(Optional.of(commit));

    String branchName = "main";
    Branch branch = new Branch();
    branch.setName(branchName);
    when(branchRepository.findByNameAndRepositoryRepositoryId(branchName, repoId))
        .thenReturn(Optional.of(branch));

    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));

    String login = "testuser";
    User user = new User();
    user.setLogin(login);
    when(userRepository.findByLoginIgnoreCase(login)).thenReturn(Optional.of(user));

    when(authService.getPreferredUsername()).thenReturn(login);

    ReleaseCandidate saved = new ReleaseCandidate();
    String rcName = "rc-1.0";
    saved.setName(rcName);
    saved.setCommit(commit);
    saved.setBranch(branch);
    saved.setRepository(repo);
    saved.setCreatedBy(user);
    saved.setCreatedAt(OffsetDateTime.now());
    when(releaseCandidateRepository.save(any(ReleaseCandidate.class))).thenReturn(saved);

    ReleaseCandidateCreateDto dto =
        new ReleaseCandidateCreateDto(rcName, commitSha, branchName);

    // Act
    ReleaseInfoListDto result = service.createReleaseCandidate(dto);

    // Assert
    Assertions.assertNotNull(result);
    assertEquals(rcName, result.name());
    verify(releaseCandidateRepository).save(any(ReleaseCandidate.class));
  }

  @Test
  void createReleaseCandidate_nameExists_throwsException() {
    String rcName = "rc-1.0";
    when(releaseCandidateRepository.existsByRepositoryRepositoryIdAndName(repoId, rcName))
        .thenReturn(true);

    ReleaseCandidateCreateDto dto =
        new ReleaseCandidateCreateDto(rcName, "sha", "main");

    // Act & Assert
    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class, () -> service.createReleaseCandidate(dto));
    assertTrue(ex.getMessage().contains("already exists"));
  }

  @Test
  void evaluateReleaseCandidate_userNull_throwsException() {
    String rcName = "rc-2.0";
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(rcName);
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, rcName))
        .thenReturn(Optional.of(rc));
    when(authService.getUserFromGithubId()).thenReturn(null);

    // Act & Assert
    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class,
            () -> service.evaluateReleaseCandidate(rcName, true, "comment"));
    assertTrue(ex.getMessage().contains("User not found"));
  }


  @Test
  void evaluateReleaseCandidate_newEvaluation_savesWorkingAndComment() {
    String rcName = "rc-2.0";
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(rcName);
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, rcName))
        .thenReturn(Optional.of(rc));

    User user = new User();
    user.setId(42L);
    when(authService.getUserFromGithubId()).thenReturn(user);

    when(
        releaseCandidateEvaluationRepository.findByReleaseCandidateAndEvaluatedById(
            rc, 42L))
        .thenReturn(Optional.empty());

    ArgumentCaptor<ReleaseCandidateEvaluation> captor =
        ArgumentCaptor.forClass(ReleaseCandidateEvaluation.class);

    boolean isWorking = true;
    String comment = "Good to go";

    // Act
    service.evaluateReleaseCandidate(rcName, isWorking, comment);

    // Assert
    verify(releaseCandidateEvaluationRepository).save(captor.capture());
    ReleaseCandidateEvaluation saved = captor.getValue();
    assertTrue(saved.isWorking());
    assertEquals(comment, saved.getComment());
    assertEquals(user, saved.getEvaluatedBy());
    assertEquals(rc, saved.getReleaseCandidate());
  }

  @Test
  void evaluateReleaseCandidate_existingEvaluation_updatesFields() {
    String rcName = "rc-2.0";
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(rcName);
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, rcName))
        .thenReturn(Optional.of(rc));

    User user = new User();
    user.setId(99L);
    when(authService.getUserFromGithubId()).thenReturn(user);

    ReleaseCandidateEvaluation existing = new ReleaseCandidateEvaluation();
    existing.setReleaseCandidate(rc);
    existing.setEvaluatedBy(user);
    when(
        releaseCandidateEvaluationRepository.findByReleaseCandidateAndEvaluatedById(
            rc, 99L))
        .thenReturn(Optional.of(existing));

    boolean isWorking = false;
    String comment = "Not working";
    // Act
    service.evaluateReleaseCandidate(rcName, isWorking, comment);

    // Assert
    verify(releaseCandidateEvaluationRepository).save(existing);
    assertFalse(existing.isWorking());
    assertEquals(comment, existing.getComment());
  }

  @Test
  void deleteReleaseCandidateByName_existing_deletesAndReturnsDto() {
    String rcName = "rc-delete";
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(rcName);
    Commit commit = new Commit();
    commit.setSha("x");
    rc.setCommit(commit);
    rc.setCreatedAt(OffsetDateTime.now());
    User creator = new User();
    creator.setLogin("deleter");
    rc.setCreatedBy(creator);
    rc.setRepository(new GitRepository());

    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, rcName))
        .thenReturn(Optional.of(rc));

    // Act
    ReleaseInfoListDto dto = service.deleteReleaseCandidateByName(rcName);

    // Assert
    assertNotNull(dto);
    assertEquals(rcName, dto.name());
    verify(releaseCandidateRepository)
        .deleteByRepositoryRepositoryIdAndName(repoId, rcName);
  }

  @Test
  void deleteReleaseCandidateByName_notFound_throwsException() {
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, "nope"))
        .thenReturn(Optional.empty());

    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class, () -> service.deleteReleaseCandidateByName("nope"));
    assertTrue(ex.getMessage().contains("could not be found"));
  }

  @Test
  void publishReleaseDraft_noBody_usesGenerateReleaseNotes_andProcesses() throws Exception {
    String tagName = "v-empty";
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    repo.setNameWithOwner("owner/repo");
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(tagName);
    rc.setRepository(repo);
    Commit commit = new Commit();
    commit.setSha("shaValue");
    rc.setCommit(commit);
    rc.setBody(null);
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, tagName))
        .thenReturn(Optional.of(rc));
    when(authService.getPreferredUsername()).thenReturn("testuser");
    // Spy to stub generateReleaseNotes
    ReleaseInfoService spyService = Mockito.spy(service);
    doReturn("generated-notes")
        .when(spyService).generateReleaseNotes(tagName);
    // Stub GH interactions
    GHRepository mockGhRepo = mock(GHRepository.class);
    when(gitHubService.getRepository("owner/repo")).thenReturn(mockGhRepo);
    GHRelease ghRelease = mock(GHRelease.class);
    when(
        gitHubService.createReleaseOnBehalfOfUser(
            "owner/repo", tagName, "shaValue", tagName, "generated-notes", true, "testuser"))
        .thenReturn(ghRelease);
    // Act
    spyService.publishReleaseDraft(tagName);
    // Assert
    verify(gitHubReleaseSyncService).processRelease(eq(ghRelease), eq(mockGhRepo));
  }

  @Test
  void publishReleaseDraft_createReleaseThrows_throwsReleaseCandidateException()
      throws IOException {
    String tagName = "v-error";
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    repo.setNameWithOwner("owner/repo");
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(tagName);
    rc.setRepository(repo);
    Commit commit = new Commit();
    commit.setSha("shaValue");
    rc.setCommit(commit);
    rc.setBody(null);
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, tagName))
        .thenReturn(Optional.of(rc));
    when(authService.getPreferredUsername()).thenReturn("testuser");
    // Spy to stub generateReleaseNotes
    ReleaseInfoService spyService = Mockito.spy(service);
    doReturn("generated-notes")
        .when(spyService).generateReleaseNotes(tagName);
    // Stub GH interactions to throw
    when(gitHubService.createReleaseOnBehalfOfUser(
        "owner/repo", tagName, "shaValue", tagName, "generated-notes", true, "testuser"))
        .thenThrow(new IOException("GH create error"));
    // Act & Assert
    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class,
            () -> spyService.publishReleaseDraft(tagName));
    assertTrue(ex.getMessage().contains("Release candidate could not be pushed to GitHub."));
  }

  @Test
  void publishReleaseDraft_withBody_callsGitHubAndProcess() throws IOException {
    String tagName = "v-released";
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    repo.setNameWithOwner("owner/repo");

    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(tagName);
    rc.setRepository(repo);
    Commit commit = new Commit();
    commit.setSha("shaValue");
    rc.setCommit(commit);
    rc.setBody("Custom release notes");

    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, tagName))
        .thenReturn(Optional.of(rc));
    when(authService.getPreferredUsername()).thenReturn("testuser");

    // Stub getRepository(...) to return a non‐null GHRepository
    GHRepository mockGhRepo = mock(GHRepository.class);
    when(gitHubService.getRepository("owner/repo")).thenReturn(mockGhRepo);

    GHRelease ghRelease = mock(GHRelease.class);
    when(
        gitHubService.createReleaseOnBehalfOfUser(
            "owner/repo", tagName, "shaValue", tagName, "Custom release notes", true, "testuser"))
        .thenReturn(ghRelease);

    // Act
    service.publishReleaseDraft(tagName);

    // Assert: now second argument to processRelease(...) will be mockGhRepo instead of null
    verify(gitHubReleaseSyncService).processRelease(eq(ghRelease), eq(mockGhRepo));
  }

  @Test
  void publishReleaseDraft_noCandidate_throwsReleaseCandidateException() {
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(new GitRepository()));
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, "nope"))
        .thenReturn(Optional.empty());

    // Act & Assert: the code will now throw ReleaseCandidateException from the candidate lookup
    assertThrows(
        ReleaseCandidateException.class,
        () -> service.publishReleaseDraft("nope"));
  }

  @Test
  void generateReleaseNotes_publishesNewNotes() throws IOException {
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    repo.setNameWithOwner("owner/repo");

    String tagName = "v2.0";
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
    when(releaseRepository.findByTagNameAndRepositoryRepositoryId(tagName, repoId))
        .thenReturn(Optional.empty());

    ReleaseCandidate rc = new ReleaseCandidate();
    Commit c = new Commit();
    c.setSha("shaRC");
    rc.setCommit(c);
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, tagName))
        .thenReturn(Optional.of(rc));

    when(gitHubService.generateReleaseNotes("owner/repo", tagName, "shaRC"))
        .thenReturn("Generated notes");

    // Act
    String notes = service.generateReleaseNotes(tagName);

    // Assert
    assertEquals("Generated notes", notes);
  }

  @Test
  void generateReleaseNotes_apiFails_throwsException() throws IOException {
    GitRepository repo = new GitRepository();
    repo.setRepositoryId(repoId);
    repo.setNameWithOwner("owner/repo");

    String tagName = "v2.1";
    when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
    when(releaseRepository.findByTagNameAndRepositoryRepositoryId(tagName, repoId))
        .thenReturn(Optional.empty());

    // Provide a ReleaseCandidate whose getCommit() is non‐null
    ReleaseCandidate rc = new ReleaseCandidate();
    Commit c = new Commit();
    c.setSha("shaRC");
    rc.setCommit(c);
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, tagName))
        .thenReturn(Optional.of(rc));

    // Make the GH call throw IOException
    when(gitHubService.generateReleaseNotes("owner/repo", tagName, "shaRC"))
        .thenThrow(new IOException("GitHub error"));

    // Act & Assert
    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class, () -> service.generateReleaseNotes(tagName));
    assertTrue(ex.getMessage().contains("Failed to generate release notes"));
  }

  @Test
  void updateReleaseNotes_successfulUpdate() {

    String tagName = "update-rc";
    String newBody = "Updated notes";

    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(tagName);
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, tagName))
        .thenReturn(Optional.of(rc));

    // Act
    service.updateReleaseNotes(tagName, newBody);

    // Assert
    verify(releaseCandidateRepository).save(rc);
    assertEquals(newBody, rc.getBody());
  }

  @Test
  void updateReleaseNotes_notFound_throwsException() {
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(repoId, "nope"))
        .thenReturn(Optional.empty());

    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class,
            () -> service.updateReleaseNotes("nope", "body"));
    assertTrue(ex.getMessage().contains("Release candidate not found"));
  }

  @Test
  void updateReleaseName_successfulRename() {
    String current = "oldName";
    String updated = "newName";
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(current);
    when(
        releaseCandidateRepository.findByRepositoryRepositoryIdAndName(
            repoId, current))
        .thenReturn(Optional.of(rc));
    when(
        releaseCandidateRepository.existsByRepositoryRepositoryIdAndName(
            repoId, updated))
        .thenReturn(false);

    // Act
    service.updateReleaseName(current, updated);

    // Assert
    verify(releaseCandidateRepository).save(rc);
    assertEquals(updated, rc.getName());
  }

  @Test
  void updateReleaseName_sameName_noChange() {

    service.updateReleaseName("same", "same");
    // No exception; repository should not be called
    verify(releaseCandidateRepository, never()).save(any());
  }

  @Test
  void updateReleaseName_newNameExists_throwsException() {
    String current = "rcA";
    String updated = "rcB";
    when(
        releaseCandidateRepository.existsByRepositoryRepositoryIdAndName(
            repoId, updated))
        .thenReturn(true);

    // Act & Assert
    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class,
            () -> service.updateReleaseName(current, updated));
    assertTrue(ex.getMessage().contains("already exists"));
  }

  @Test
  void updateReleaseName_alreadyPublished_throwsException() {
    String current = "rcC";
    ReleaseCandidate rc = new ReleaseCandidate();
    rc.setName(current);
    Release rel = new Release();
    rc.setRelease(rel); // indicates already published

    when(
        releaseCandidateRepository.findByRepositoryRepositoryIdAndName(
            repoId, current))
        .thenReturn(Optional.of(rc));

    String updated = "rcD";
    // Act & Assert
    ReleaseCandidateException ex =
        assertThrows(
            ReleaseCandidateException.class,
            () -> service.updateReleaseName(current, updated));
    assertTrue(ex.getMessage().contains("Cannot update name"));
  }
}
