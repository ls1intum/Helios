package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@Transactional
public class GitRepoSettingsService {

  private final GitRepoSettingsRepository gitRepoRepository;
  private final GitRepoRepository gitRepository;
  @Lazy private final EnvironmentService environmentService;

  public GitRepoSettingsService(
      GitRepoSettingsRepository gitRepoRepository,
      GitRepoRepository gitRepository,
      @Lazy EnvironmentService environmentService) {
    this.gitRepoRepository = gitRepoRepository;
    this.gitRepository = gitRepository;
    this.environmentService = environmentService;
  }

  public Optional<GitRepoSettingsDto> getOrCreateGitRepoSettingsByRepositoryId(Long repositoryId) {
    return Optional.of(
        gitRepoRepository
            .findByRepositoryRepositoryId(repositoryId)
            .map(GitRepoSettingsDto::fromGitRepoSettings)
            .orElseGet(
                () -> {
                  GitRepository gitRepo =
                      gitRepository
                          .findByRepositoryId(repositoryId)
                          .orElseThrow(
                              () ->
                                  new IllegalArgumentException(
                                      "Repository with id "
                                          + repositoryId
                                          + " not found while creating settings"));

                  GitRepoSettings newRepoSettings = new GitRepoSettings();
                  newRepoSettings.setRepository(gitRepo);
                  gitRepoRepository.save(newRepoSettings);

                  return GitRepoSettingsDto.fromGitRepoSettings(newRepoSettings);
                }));
  }

  public Optional<GitRepoSettingsDto> updateGitRepoSettings(
      @PathVariable Long repositoryId, @RequestBody GitRepoSettingsDto gitRepoSettingsDto) {
    GitRepoSettings gitRepoSettings =
        gitRepoRepository
            .findByRepositoryRepositoryId(repositoryId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "GitRepoSettings not found for repository with id " + repositoryId));

    if (gitRepoSettingsDto.lockExpirationThreshold() != null) {
      gitRepoSettings.setLockExpirationThreshold(gitRepoSettingsDto.lockExpirationThreshold());
    }
    if (gitRepoSettingsDto.lockReservationThreshold() != null) {
      gitRepoSettings.setLockReservationThreshold(gitRepoSettingsDto.lockReservationThreshold());
    }
    gitRepoRepository.save(gitRepoSettings);

    // Get all locked Environments and set getLockWillExpireAt and set LockReservationExpiresAt
    environmentService.updateLockExpirationAndReservation(repositoryId);

    return Optional.of(GitRepoSettingsDto.fromGitRepoSettings(gitRepoSettings));
  }
}
