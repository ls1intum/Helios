package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class GitRepoSettingsService {

  private final GitRepoRepository gitRepoRepository;

  public GitRepoSettingsService(GitRepoRepository gitRepoRepository) {
    this.gitRepoRepository = gitRepoRepository;
  }
}
