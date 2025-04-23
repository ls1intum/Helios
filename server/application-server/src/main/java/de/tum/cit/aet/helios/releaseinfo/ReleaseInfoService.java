package de.tum.cit.aet.helios.releaseinfo;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchInfoDto;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.commit.CommitInfoDto;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.sync.GitHubDataSyncOrchestrator;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.releaseinfo.ReleaseInfoDetailsDto.ReleaseCandidateEvaluationDto;
import de.tum.cit.aet.helios.releaseinfo.release.ReleaseDto;
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
import de.tum.cit.aet.helios.user.UserInfoDto;
import de.tum.cit.aet.helios.user.UserRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHCompare.Commit;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor
public class ReleaseInfoService {
  private final GitHubService gitHubService;
  private final GitRepoRepository gitRepoRepository;
  private final ReleaseCandidateRepository releaseCandidateRepository;
  private final ReleaseRepository releaseRepository;
  private final CommitRepository commitRepository;
  private final DeploymentRepository deploymentRepository;
  private final BranchRepository branchRepository;
  private final UserRepository userRepository;
  private final ReleaseCandidateEvaluationRepository releaseCandidateEvaluationRepository;
  private final AuthService authService;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final GitHubDataSyncOrchestrator gitHubDataSyncOrchestrator;
  private final GitHubReleaseSyncService gitHubReleaseSyncService;

  public List<ReleaseInfoListDto> getAllReleaseInfos() {
    return releaseCandidateRepository.findAllByOrderByCreatedAtDesc().stream()
        .map(ReleaseInfoListDto::fromReleaseCandidate)
        .toList();
  }

  /**
   * Returns all deployments for a given release candidate, meaning a specific commit and
   * repository. It considers HeliosDeployment and Deployment entities and returns the latest one
   * for each environment.
   *
   * @param candidate The release candidate to get deployments for
   * @return A list of LatestDeploymentUnion objects, each representing the latest deployment for an
   *     environment
   */
  private List<LatestDeploymentUnion> getCandidateDeployments(final ReleaseCandidate candidate) {
    Map<Long, LatestDeploymentUnion> deploymentsByEnvironment = new HashMap<>();

    List<HeliosDeployment> heliosDeployments =
        heliosDeploymentRepository.findByRepositoryIdAndSha(
            candidate.getRepository().getRepositoryId(), candidate.getCommit().getSha());

    List<Deployment> deployments =
        deploymentRepository.findByRepositoryRepositoryIdAndSha(
            candidate.getRepository().getRepositoryId(), candidate.getCommit().getSha());

    for (HeliosDeployment heliosDeployment : heliosDeployments) {
      deploymentsByEnvironment.put(
          heliosDeployment.getEnvironment().getId(),
          LatestDeploymentUnion.heliosDeployment(heliosDeployment));
    }

    for (Deployment deployment : deployments) {
      if (!deploymentsByEnvironment.containsKey(deployment.getEnvironment().getId())) {
        deploymentsByEnvironment.put(
            deployment.getEnvironment().getId(), LatestDeploymentUnion.realDeployment(deployment));
        continue;
      }

      LatestDeploymentUnion latestDeploymentUnion =
          deploymentsByEnvironment.get(deployment.getEnvironment().getId());

      if (latestDeploymentUnion.getCreatedAt().isAfter(deployment.getCreatedAt())) {
        continue;
      }

      deploymentsByEnvironment.put(
          deployment.getEnvironment().getId(), LatestDeploymentUnion.realDeployment(deployment));
    }

    return new ArrayList<>(deploymentsByEnvironment.values());
  }

  public ReleaseInfoDetailsDto getReleaseInfoByName(String name) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    return releaseCandidateRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(
            releaseCandidate -> {
              var deployments =
                  this.getCandidateDeployments(releaseCandidate).stream()
                      .map(ReleaseInfoDetailsDto.ReleaseCandidateDeploymentDto::fromDeployment)
                      .toList();

              return new ReleaseInfoDetailsDto(
                  releaseCandidate.getName(),
                  CommitInfoDto.fromCommit(releaseCandidate.getCommit()),
                  releaseCandidate.getBranch() != null
                      ? BranchInfoDto.fromBranch(releaseCandidate.getBranch(), commitRepository)
                      : null,
                  deployments,
                  releaseCandidate.getEvaluations().stream()
                      .map(ReleaseCandidateEvaluationDto::fromEvaluation)
                      .toList(),
                  releaseCandidate.getRelease() != null
                      ? ReleaseDto.fromRelease(releaseCandidate.getRelease())
                      : null,
                  UserInfoDto.fromUser(releaseCandidate.getCreatedBy()),
                  releaseCandidate.getCreatedAt(),
                  releaseCandidate.getBody());
            })
        .orElseThrow(() -> new ReleaseCandidateException("ReleaseCandidate not found"));
  }

  public CommitsSinceReleaseCandidateDto getCommitsFromBranchSinceLastReleaseCandidate(
      String branchName) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    try {
      final GitRepository repository =
          gitRepoRepository
              .findById(repositoryId)
              .orElseThrow(() -> new ReleaseCandidateException("Repository not found"));

      final GHRepository githubRepository =
          gitHubService.getRepository(repository.getNameWithOwner());

      final Branch branch =
          branchRepository
              .findByRepositoryRepositoryIdAndName(repositoryId, branchName)
              .orElseThrow(() -> new ReleaseCandidateException("Branch not found"));

      final ReleaseCandidate lastReleaseCandidate =
          releaseCandidateRepository.findByRepository(repository).stream()
              .sorted(ReleaseCandidate::compareToByDate)
              .findFirst()
              .orElseGet(() -> null);

      if (lastReleaseCandidate == null) {
        return new CommitsSinceReleaseCandidateDto(-1, -1, new ArrayList<>(), null);
      }

      final GHCompare compare =
          githubRepository.getCompare(
              lastReleaseCandidate.getCommit().getSha(), branch.getCommitSha());

      final List<CommitsSinceReleaseCandidateDto.CompareCommitInfoDto> commitInfoDtos =
          new ArrayList<>();

      for (Commit commit : compare.getCommits()) {
        var innerCommit = commit.getCommit();

        commitInfoDtos.add(
            new CommitsSinceReleaseCandidateDto.CompareCommitInfoDto(
                commit.getSHA1(),
                innerCommit.getMessage(),
                innerCommit.getCommitter().getName(),
                innerCommit.getCommitter().getEmail(),
                commit.getHtmlUrl().toString()));
      }

      // Get compare url
      String compareUrl = compare.getHtmlUrl().toString();

      return new CommitsSinceReleaseCandidateDto(
          compare.getAheadBy(), compare.getBehindBy(), commitInfoDtos, compareUrl);
    } catch (IOException e) {
      log.error("Failed to compare commits for branch {}: {}", branchName, e.getMessage());
      throw new ReleaseCandidateException("Failed to fetch compare commit data from GitHub");
    }
  }

  public ReleaseInfoListDto createReleaseCandidate(ReleaseCandidateCreateDto releaseCandidate) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    final String login = authService.getPreferredUsername();

    if (releaseCandidateRepository.existsByRepositoryRepositoryIdAndName(
            repositoryId, releaseCandidate.name())
        == true) {
      throw new ReleaseCandidateException("ReleaseCandidate with this name already exists");
    }

    ReleaseCandidate newReleaseCandidate = new ReleaseCandidate();
    newReleaseCandidate.setName(releaseCandidate.name());
    newReleaseCandidate.setCommit(
        commitRepository
            .findByShaAndRepositoryRepositoryId(releaseCandidate.commitSha(), repositoryId)
            .orElseThrow(() -> new ReleaseCandidateException("Commit not found")));
    newReleaseCandidate.setBranch(
        branchRepository
            .findByNameAndRepositoryRepositoryId(releaseCandidate.branchName(), repositoryId)
            .orElseThrow(() -> new ReleaseCandidateException("Branch not found")));
    newReleaseCandidate.setRepository(
        gitRepoRepository
            .findById(repositoryId)
            .orElseThrow(() -> new ReleaseCandidateException("Repository not found")));
    newReleaseCandidate.setCreatedBy(
        userRepository
            .findByLoginIgnoreCase(login)
            .orElseGet(() -> gitHubDataSyncOrchestrator.syncUser(login)));
    newReleaseCandidate.setCreatedAt(OffsetDateTime.now());
    return ReleaseInfoListDto.fromReleaseCandidate(
        releaseCandidateRepository.save(newReleaseCandidate));
  }

  public void evaluateReleaseCandidate(String name, boolean isWorking) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    final ReleaseCandidate releaseCandidate =
        releaseCandidateRepository
            .findByRepositoryRepositoryIdAndName(repositoryId, name)
            .orElseThrow(() -> new ReleaseCandidateException("ReleaseCandidate not found"));

    final User user = authService.getUserFromGithubId();

    if (user == null) {
      throw new ReleaseCandidateException("User not found");
    }

    final ReleaseCandidateEvaluation evaluation =
        releaseCandidateEvaluationRepository
            .findByReleaseCandidateAndEvaluatedById(releaseCandidate, user.getId())
            .orElseGet(
                () -> {
                  ReleaseCandidateEvaluation newEvaluation = new ReleaseCandidateEvaluation();
                  newEvaluation.setReleaseCandidate(releaseCandidate);
                  newEvaluation.setEvaluatedBy(user);
                  return newEvaluation;
                });

    evaluation.setWorking(isWorking);
    releaseCandidateEvaluationRepository.save(evaluation);
  }

  public ReleaseInfoListDto deleteReleaseCandidateByName(String name) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    ReleaseInfoListDto rc =
        releaseCandidateRepository
            .findByRepositoryRepositoryIdAndName(repositoryId, name)
            .map(ReleaseInfoListDto::fromReleaseCandidate)
            .orElseThrow(
                () -> new ReleaseCandidateException("ReleaseCandidate could not be found."));

    releaseCandidateRepository.deleteByRepositoryRepositoryIdAndName(repositoryId, name);

    return rc;
  }

  public void publishReleaseDraft(String tagName) {
    Long repositoryId = RepositoryContext.getRepositoryId();
    GitRepository repository = gitRepoRepository.findById(repositoryId).orElseThrow();
    ReleaseCandidate releaseCandidate =
        releaseCandidateRepository
            .findByRepositoryRepositoryIdAndName(repositoryId, tagName)
            .orElseThrow(() -> new ReleaseCandidateException("Release candidate not found"));

    try {
      // Get the release notes (body)
      String releaseNotes;
      if (releaseCandidate.getBody() != null) {
        releaseNotes = releaseCandidate.getBody();
      } else {
        releaseNotes = generateReleaseNotes(tagName);
      }

      // Get the current user's GitHub login
      String githubUserLogin = authService.getPreferredUsername();

      // Create the release using the user's token
      GHRelease ghRelease =
          gitHubService.createReleaseOnBehalfOfUser(
              repository.getNameWithOwner(),
              tagName,
              releaseCandidate.getCommit().getSha(),
              tagName, // Using tagName as the release name
              releaseNotes,
              true,
              githubUserLogin);

      // Process the release in the system
      gitHubReleaseSyncService.processRelease(
          ghRelease, gitHubService.getRepository(repository.getNameWithOwner()));

    } catch (IOException e) {
      log.error("Failed to publish release: {}", e.getMessage());
      throw new ReleaseCandidateException("Release candidate could not be pushed to GitHub.");
    }
  }

  public String generateReleaseNotes(String tagName) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    final GitRepository repository =
        gitRepoRepository
            .findById(repositoryId)
            .orElseThrow(() -> new ReleaseCandidateException("Repository not found"));

    String targetCommitish = null;

    // If release is not yet published, get commit SHA from release candidate
    if (!releaseRepository
        .findByTagNameAndRepositoryRepositoryId(tagName, repositoryId)
        .isPresent()) {
      targetCommitish =
          releaseCandidateRepository
              .findByRepositoryRepositoryIdAndName(repositoryId, tagName)
              .map(rc -> rc.getCommit().getSha())
              .orElseThrow(() -> new ReleaseCandidateException("Release candidate not found"));
    }

    try {
      return gitHubService.generateReleaseNotes(
          repository.getNameWithOwner(), tagName, targetCommitish);
    } catch (IOException e) {
      log.error("Failed to generate release notes: {}", e.getMessage());
      throw new ReleaseCandidateException("Failed to generate release notes");
    }
  }

  public void updateReleaseNotes(String tagName, String releaseNotes) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    releaseCandidateRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, tagName)
        .ifPresentOrElse(
            releaseCandidate -> {
              releaseCandidate.setBody(releaseNotes);
              releaseCandidateRepository.save(releaseCandidate);
            },
            () -> {
              throw new ReleaseCandidateException("Release candidate not found");
            });
  }
}
