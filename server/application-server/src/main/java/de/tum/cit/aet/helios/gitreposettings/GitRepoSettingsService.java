package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestInfoDTO;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GitRepoSettingsService {

    private final GitRepoRepository gitRepoRepository;

    public GitRepoSettingsService(GitRepoRepository gitRepoRepository) {
        this.gitRepoRepository = gitRepoRepository;
    }

}