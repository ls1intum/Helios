package de.tum.cit.aet.helios.releaseinfo.release.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.commit.github.GitHubCommitSyncService;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.releaseinfo.release.Release;
import de.tum.cit.aet.helios.releaseinfo.release.ReleaseRepository;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidate;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTagObject;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubReleaseSyncServiceTest {

  @Mock private ReleaseRepository releaseRepository;
  @Mock private GitHubReleaseConverter releaseConverter;
  @Mock private GitHubCommitSyncService commitSyncService;
  @Mock private GitRepoRepository gitRepoRepository;
  @Mock private ReleaseCandidateRepository releaseCandidateRepository;
  @Mock private CommitRepository commitRepository;
  @Mock private GitHubService gitHubService;

  @Mock private GHRepository ghRepository;
  @Mock private GHRepository currentRepository;
  @Mock private GHRef ref;
  @Mock private GHRef.GHObject refObject;

  @InjectMocks private GitHubReleaseSyncService service;

  private GHRelease ghRelease;
  private GitRepository repository;
  private Release release;

  @BeforeEach
  void setUp() throws IOException {
    repository = new GitRepository();
    repository.setRepositoryId(42L);
    repository.setNameWithOwner("owner/repo");

    release = new Release();
    release.setId(1L);

    ghRelease =
        new GHRelease() {
          @Override
          public long getId() {
            return 1L;
          }

          @Override
          public String getTagName() {
            return "v1.0.0";
          }
        };

    when(ghRepository.getFullName()).thenReturn("owner/repo");

    when(gitRepoRepository.findByNameWithOwner("owner/repo")).thenReturn(repository);
    when(releaseRepository.findById(1L)).thenReturn(Optional.of(release));
    when(releaseRepository.saveAndFlush(release)).thenReturn(release);
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndName(42L, "v1.0.0"))
        .thenReturn(Optional.empty());

    when(gitHubService.getRepository("owner/repo")).thenReturn(currentRepository);
    when(currentRepository.getRef("tags/v1.0.0")).thenReturn(ref);
    when(ref.getObject()).thenReturn(refObject);
  }

  @Test
  void processRelease_annotatedTag_resolvesCommitAndSavesReleaseCandidate() throws IOException {
    Commit commit = new Commit();
    commit.setSha("commit-sha");

    when(refObject.getType()).thenReturn("tag");
    when(refObject.getSha()).thenReturn("tag-sha");
    GHTagObject tagObject = mock(GHTagObject.class);
    when(currentRepository.getTagObject("tag-sha")).thenReturn(tagObject);
    GHRef.GHObject tagTargetObject = mock(GHRef.GHObject.class);
    when(tagObject.getObject()).thenReturn(tagTargetObject);
    when(tagTargetObject.getType()).thenReturn("commit");
    when(tagTargetObject.getSha()).thenReturn("commit-sha");
    when(commitRepository.findByShaAndRepositoryRepositoryId("commit-sha", 42L))
        .thenReturn(Optional.of(commit));
    when(releaseCandidateRepository.saveAndFlush(any(ReleaseCandidate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.processRelease(ghRelease, ghRepository);

    ArgumentCaptor<ReleaseCandidate> captor = ArgumentCaptor.forClass(ReleaseCandidate.class);
    verify(releaseCandidateRepository).saveAndFlush(captor.capture());
    ReleaseCandidate savedReleaseCandidate = captor.getValue();
    assertEquals("v1.0.0", savedReleaseCandidate.getName());
    assertSame(repository, savedReleaseCandidate.getRepository());
    assertSame(commit, savedReleaseCandidate.getCommit());
    verify(currentRepository, never()).getCommit(anyString());
  }

  @Test
  void processRelease_commitLookupFails_doesNotSaveReleaseCandidate() throws IOException {
    when(refObject.getType()).thenReturn("commit");
    when(refObject.getSha()).thenReturn("missing-commit-sha");
    when(commitRepository.findByShaAndRepositoryRepositoryId("missing-commit-sha", 42L))
        .thenReturn(Optional.empty());
    when(currentRepository.getCommit("missing-commit-sha"))
        .thenThrow(new IOException("No commit found"));

    service.processRelease(ghRelease, ghRepository);

    verify(releaseCandidateRepository, never()).saveAndFlush(any(ReleaseCandidate.class));
  }

  @Test
  void processRelease_lightweightTag_syncsCommitWhenMissingLocally() throws IOException {
    Commit commit = new Commit();
    commit.setSha("commit-sha");

    when(refObject.getType()).thenReturn("commit");
    when(refObject.getSha()).thenReturn("commit-sha");
    when(commitRepository.findByShaAndRepositoryRepositoryId("commit-sha", 42L))
        .thenReturn(Optional.empty());
    GHCommit ghCommit = mock(GHCommit.class);
    when(currentRepository.getCommit("commit-sha")).thenReturn(ghCommit);
    when(commitSyncService.processCommit(ghCommit, ghRepository)).thenReturn(commit);
    when(releaseCandidateRepository.saveAndFlush(any(ReleaseCandidate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.processRelease(ghRelease, ghRepository);

    verify(commitSyncService).processCommit(ghCommit, ghRepository);
    verify(currentRepository, never()).getTagObject(anyString());
    verify(releaseCandidateRepository).saveAndFlush(any(ReleaseCandidate.class));
  }
}
