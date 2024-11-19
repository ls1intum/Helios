package de.tum.cit.aet.helios.gitprovider.pullrequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class PullRequestService {

    private final PullRequestRepository pullRequestRepository;

    @Autowired
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
}