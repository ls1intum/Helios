package de.tum.cit.aet.helios.gitreposettings;

import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@Transactional
public class GitRepoSettingsService {

  private final GitRepoSettingsRepository gitRepoRepository;

  public GitRepoSettingsService(GitRepoSettingsRepository gitRepoRepository) {
    this.gitRepoRepository = gitRepoRepository;
  }

  public Optional<GitRepoSettingsDto> getGitRepoSettingsByRepositoryId(Long repositoryId) {
    return gitRepoRepository
        .findByRepositoryRepositoryId(repositoryId)
        .map(GitRepoSettingsDto::fromGitRepoSettings);
  }

  public Optional<GitRepoSettingsDto> updateGitRepoSettings(
      @PathVariable Long repositoryId, @RequestBody GitRepoSettingsDto gitRepoSettingsDto) {
    return gitRepoRepository
        .findByRepositoryRepositoryId(repositoryId)
        .map(
            gitRepoSettings -> {
              if (gitRepoSettingsDto.lockExpirationThreshold() != null) {
                gitRepoSettings.setLockExpirationThreshold(
                    gitRepoSettingsDto.lockExpirationThreshold());
              }
              if (gitRepoSettingsDto.lockReservationThreshold() != null) {
                gitRepoSettings.setLockReservationThreshold(
                    gitRepoSettingsDto.lockReservationThreshold());
              }
              gitRepoRepository.save(gitRepoSettings);
              return GitRepoSettingsDto.fromGitRepoSettings(gitRepoSettings);
            });
  }
}
