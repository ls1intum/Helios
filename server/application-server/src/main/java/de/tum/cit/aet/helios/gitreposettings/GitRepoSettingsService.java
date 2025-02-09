package de.tum.cit.aet.helios.gitreposettings;

import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class GitRepoSettingsService {

  private final GitRepoSettingsRepository gitRepoRepository;

  public GitRepoSettingsService(GitRepoSettingsRepository gitRepoRepository) {
    this.gitRepoRepository = gitRepoRepository;
  }

  public Optional<GitRepoSettings> getGitRepoSettingsByRepositoryId(Long repositoryId) {
    return gitRepoRepository.findByRepositoryRepositoryId(repositoryId);
  }
}
