package de.tum.cit.aet.helios.releaseinfo.release.github;

import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.commit.github.GitHubCommitSyncService;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.releaseinfo.release.ReleaseRepository;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidate;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.user.User;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTagObject;
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
    processRelease(ghRelease, ghRepository, null);
  }

  /**
   * Processes a single GitHub release and stores the creator when the release is first seen.
   *
   * @param ghRelease the GitHub release to process
   * @param ghRepository the repository that owns the release
   * @param creator the creator to persist for newly created releases
   */
  @Transactional
  public void processRelease(GHRelease ghRelease, GHRepository ghRepository, User creator) {
    var repository = gitRepoRepository.findByNameWithOwner(ghRepository.getFullName());
    var existingRelease = releaseRepository.findById(ghRelease.getId());
    boolean isNewRelease = existingRelease.isEmpty();
    var result =
        existingRelease
            .map(release -> releaseConverter.update(ghRelease, release))
            .orElseGet(() -> releaseConverter.convert(ghRelease));

    if (result == null) {
      return;
    }

    result.setRepository(repository);
    if (isNewRelease && creator != null) {
      result.setCreator(creator);
    }

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
                GHRepository currentRepository =
                    gitHubService.getRepository(ghRepository.getFullName());
                final String commitSha =
                    resolveCommitShaForTag(currentRepository, ghRelease.getTagName());

                if (commitSha == null) {
                  return null;
                }

                final Commit commit =
                    commitRepository
                        .findByShaAndRepositoryRepositoryId(commitSha, repository.getRepositoryId())
                        .orElseGet(
                            () -> {
                              try {
                                return commitSyncService.processCommit(
                                    currentRepository.getCommit(commitSha), ghRepository);
                              } catch (IOException e) {
                                log.error(
                                    "Failed to get commit for release candidate {}: {}",
                                    ghRelease.getTagName(),
                                    e.getMessage());
                                return null;
                              }
                            });

                if (commit == null) {
                  log.error(
                      "Skipping release candidate {} because no commit could be resolved",
                      ghRelease.getTagName());
                  return null;
                }

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

  private String resolveCommitShaForTag(GHRepository repository, String tagName)
      throws IOException {
    GHRef.GHObject object = repository.getRef("tags/" + tagName).getObject();

    // Release tags can be lightweight (points directly to commit) or annotated (points to tag
    // object). Follow tag objects until we reach the underlying commit.
    for (int depth = 0; depth < 5; depth++) {
      if ("commit".equals(object.getType())) {
        return object.getSha();
      }
      if (!"tag".equals(object.getType())) {
        log.error(
            "Unsupported tag object type '{}' for release candidate {}",
            object.getType(),
            tagName);
        return null;
      }
      GHTagObject tagObject = repository.getTagObject(object.getSha());
      object = tagObject.getObject();
    }

    log.error(
        "Failed to resolve commit for release candidate {} due to excessive tag depth", tagName);
    return null;
  }
}
