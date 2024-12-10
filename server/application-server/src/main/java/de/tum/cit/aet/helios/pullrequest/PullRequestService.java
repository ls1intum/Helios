package de.tum.cit.aet.helios.pullrequest;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class PullRequestService {

    private final PullRequestRepository pullRequestRepository;

    public PullRequestService(PullRequestRepository pullRequestRepository) {
        this.pullRequestRepository = pullRequestRepository;
    }

    public List<PullRequestInfoDTO> getAllPullRequests() {
        return pullRequestRepository.findAll().stream()
                .map(PullRequestInfoDTO::fromPullRequest)
                .collect(Collectors.toList());
    }

    public Optional<PullRequestInfoDTO> getPullRequestById(Long id) {
        return pullRequestRepository.findById(id)
                .map(PullRequestInfoDTO::fromPullRequest);
    }

    public List<PullRequestInfoDTO> getPullRequestByRepositoryId(Long repositoryId) {
        return pullRequestRepository.findByRepositoryId(repositoryId).stream()
                .map(PullRequestInfoDTO::fromPullRequest)
                .collect(Collectors.toList());
    }
}