package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@RequiredArgsConstructor
@Service
public class GitRepoSettingsService {

  private final GitRepoSettingsRepository gitRepoRepository;
  private final GitRepoRepository gitRepository;
  @Lazy private final EnvironmentService environmentService;

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

  /**
   * Read-only lookup for the settings GET endpoint: returns the persisted settings, or transient
   * defaults when none exist yet — WITHOUT writing. (getOrCreate persists a row, which a plain GET
   * must not do; callers that need the row to exist keep using getOrCreate.)
   */
  public GitRepoSettingsDto getGitRepoSettingsByRepositoryId(Long repositoryId) {
    return gitRepoRepository
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
                                  "Repository with id " + repositoryId + " not found"));
              GitRepoSettings defaults = new GitRepoSettings();
              defaults.setRepository(gitRepo);
              return GitRepoSettingsDto.fromGitRepoSettings(defaults);
            });
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
    if (gitRepoSettingsDto.packageName() != null) {
      gitRepoSettings.setPackageName(gitRepoSettingsDto.packageName());
    }
    gitRepoRepository.save(gitRepoSettings);

    // Get all locked Environments and set getLockWillExpireAt and set LockReservationExpiresAt
    environmentService.updateLockExpirationAndReservation(repositoryId);

    return Optional.of(GitRepoSettingsDto.fromGitRepoSettings(gitRepoSettings));
  }
}
