package de.tum.cit.aet.helios.releasecandidate;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchInfoDto;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.commit.CommitInfoDto;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.deployment.DeploymentDto;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.releasecandidate.ReleaseCandidateDetailsDto.ReleaseCandidateEvaluationDto;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserInfoDto;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
  private final GitHubUserSyncService userSyncService;
  private final ReleaseCandidateEvaluationRepository releaseCandidateEvaluationRepository;
  private final AuthService authService;

  public List<ReleaseCandidateInfoDto> getAllReleaseCandidates() {
    return releaseCandidateRepository.findAllByOrderByNameAsc().stream()
        .map(ReleaseCandidateInfoDto::fromReleaseCandidate)
        .toList();
  }

  public ReleaseCandidateDetailsDto getReleaseCandidateByName(String name) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    return releaseCandidateRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(
            releaseCandidate -> {
              final List<DeploymentDto> deployments =
                  deploymentRepository
                      .findByRepositoryRepositoryIdAndSha(
                          repositoryId, releaseCandidate.getCommit().getSha())
                      .stream()
                      .map(DeploymentDto::fromDeployment)
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
            .orElseGet(() -> userSyncService.syncUser(login)));
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
}
