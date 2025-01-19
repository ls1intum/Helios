package de.tum.cit.aet.helios.pullrequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PullRequestService {

  private final PullRequestRepository pullRequestRepository;

  public PullRequestService(PullRequestRepository pullRequestRepository) {
    this.pullRequestRepository = pullRequestRepository;
  }

  public List<PullRequestBaseInfoDto> getAllPullRequests() {
    return pullRequestRepository.findAllByOrderByUpdatedAtDesc().stream()
        .map(PullRequestBaseInfoDto::fromPullRequest)
        .collect(Collectors.toList());
  }

  public Optional<PullRequestInfoDto> getPullRequestById(Long id) {
    return pullRequestRepository.findById(id).map(PullRequestInfoDto::fromPullRequest);
  }

  public List<PullRequestInfoDto> getPullRequestByRepositoryId(Long repositoryId) {
    return pullRequestRepository.findByRepositoryRepositoryIdOrderByUpdatedAtDesc(repositoryId).stream()
        .map(PullRequestInfoDto::fromPullRequest)
        .collect(Collectors.toList());
  }

  public Optional<PullRequestInfoDto> getPullRequestByRepositoryIdAndNumber(
      Long repoId, Integer number) {
    return pullRequestRepository
        .findByRepositoryRepositoryIdAndNumber(repoId, number)
        .map(PullRequestInfoDto::fromPullRequest);
  }
}
