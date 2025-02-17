package de.tum.cit.aet.helios.releasecandidate;

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
import de.tum.cit.aet.helios.releasecandidate.ReleaseCandidateDetailsDto.ReleaseCandidateEvaluationDto;
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
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor
public class ReleaseCandidateService {
  private final ReleaseCandidateRepository releaseCandidateRepository;
  private final CommitRepository commitRepository;
  private final DeploymentRepository deploymentRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubService gitHubService;
  private final BranchRepository branchRepository;
  private final UserRepository userRepository;
  private final ReleaseCandidateEvaluationRepository releaseCandidateEvaluationRepository;
  private final AuthService authService;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final GitHubDataSyncOrchestrator gitHubDataSyncOrchestrator;

  public List<ReleaseCandidateInfoDto> getAllReleaseCandidates() {
    return releaseCandidateRepository.findAllByOrderByNameAsc().stream()
        .map(ReleaseCandidateInfoDto::fromReleaseCandidate)
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

  public ReleaseCandidateDetailsDto getReleaseCandidateByName(String name) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    return releaseCandidateRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(
            releaseCandidate -> {
              var deployments =
                  this.getCandidateDeployments(releaseCandidate).stream()
                      .map(ReleaseCandidateDetailsDto.ReleaseCandidateDeploymentDto::fromDeployment)
                      .toList();

              return new ReleaseCandidateDetailsDto(
                  releaseCandidate.getName(),
                  CommitInfoDto.fromCommit(releaseCandidate.getCommit()),
                  BranchInfoDto.fromBranch(releaseCandidate.getBranch()),
                  deployments,
                  releaseCandidate.getEvaluations().stream()
                      .map(ReleaseCandidateEvaluationDto::fromEvaluation)
                      .toList(),
                  UserInfoDto.fromUser(releaseCandidate.getCreatedBy()),
                  releaseCandidate.getCreatedAt());
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
        return new CommitsSinceReleaseCandidateDto(-1, new ArrayList<>());
      }

      final GHCompare compare =
          githubRepository.getCompare(
              lastReleaseCandidate.getCommit().getSha(), branch.getCommitSha());

      return new CommitsSinceReleaseCandidateDto(compare.getTotalCommits(), new ArrayList<>());
      // Add this snippet later when showing commit info
      // Arrays.stream(compare.getCommits())
      //     .map(commitConverter::convert)
      //     .map(CommitInfoDto::fromCommit)
      //     .toList()));
    } catch (IOException e) {
      log.error("Failed to compare commits for branch {}: {}", branchName, e.getMessage());
      throw new ReleaseCandidateException("Failed to fetch compare commit data from GitHub");
    }
  }

  public ReleaseCandidateInfoDto createReleaseCandidate(
      ReleaseCandidateCreateDto releaseCandidate) {
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
    return ReleaseCandidateInfoDto.fromReleaseCandidate(
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

  public ReleaseCandidateInfoDto deleteReleaseCandidateByName(String name) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    ReleaseCandidateInfoDto rc = releaseCandidateRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(ReleaseCandidateInfoDto::fromReleaseCandidate)
        .orElseThrow(() -> new ReleaseCandidateException("ReleaseCandidate could not be found."));

    releaseCandidateRepository
        .deleteByRepositoryRepositoryIdAndName(repositoryId, name);
    
    return rc;
  }
}
