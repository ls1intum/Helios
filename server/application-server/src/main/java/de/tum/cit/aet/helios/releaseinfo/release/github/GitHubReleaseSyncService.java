package de.tum.cit.aet.helios.releaseinfo.release.github;

import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.commit.github.GitHubCommitSyncService;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.releaseinfo.release.ReleaseRepository;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidate;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubReleaseSyncService {

  private final ReleaseRepository releaseRepository;
  private final GitHubReleaseConverter releaseConverter;
  private final GitHubCommitSyncService commitSyncService;
  private final GitRepoRepository gitRepoRepository;
  private final ReleaseCandidateRepository releaseCandidateRepository;
  private final CommitRepository commitRepository;
  private final GitHubService gitHubService;

  /**
   * Processes a single GitHub release by updating or creating it in the local repository. Manages
   * associations to release candidates.
   *
   * @param ghRelease the GitHub release to process
   */
  @Transactional
  public void processRelease(GHRelease ghRelease, GHRepository ghRepository) {
    var repository = gitRepoRepository.findByNameWithOwner(ghRepository.getFullName());
    var result =
        releaseRepository
            .findById(ghRelease.getId())
            .orElseGet(() -> releaseConverter.convert(ghRelease));

    if (result == null) {
      return;
    }

    result.setRepository(repository);

    releaseRepository.saveAndFlush(result);

    // Link release candidate to this release
    releaseCandidateRepository
        .findByRepositoryRepositoryIdAndName(repository.getRepositoryId(), ghRelease.getTagName())
        .map(
            releaseCandidate -> {
              releaseCandidate.setRelease(result);
              return releaseCandidateRepository.save(releaseCandidate);
            })
        .orElseGet(
            () -> {
              try {
                GHRepository currentRepository = gitHubService
                    .getRepository(ghRepository.getFullName());
                final GHRef ref = currentRepository.getRef("tags/" + ghRelease.getTagName());
                final Commit commit =
                    commitRepository
                        .findByShaAndRepositoryRepositoryId(
                            ref.getObject().getSha(), repository.getRepositoryId())
                        .orElseGet(
                            () -> {
                              try {
                                return commitSyncService.processCommit(
                                  currentRepository
                                      .getCommit(ref.getObject().getSha()), ghRepository);
                              } catch (IOException e) {
                                log.error(
                                    "Failed to get commit for release candidate {}: {}",
                                    ghRelease.getTagName(),
                                    e.getMessage());
                                return null;
                              }
                            });

                ReleaseCandidate releaseCandidate = new ReleaseCandidate();
                releaseCandidate.setRepository(repository);
                releaseCandidate.setName(ghRelease.getTagName());
                releaseCandidate.setRelease(result);
                releaseCandidate.setCommit(commit);
                releaseCandidate.setCreatedAt(result.getCreatedAt());

                return releaseCandidateRepository.saveAndFlush(releaseCandidate);
              } catch (IOException e) {
                log.error(
                    "Failed to get ref for release candidate {}: {}",
                    ghRelease.getTagName(),
                    e.getMessage());
                return null;
              }
            });
  }
}
