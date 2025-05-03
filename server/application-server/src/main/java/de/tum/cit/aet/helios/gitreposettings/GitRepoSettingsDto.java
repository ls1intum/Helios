package de.tum.cit.aet.helios.gitreposettings;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GitRepoSettingsDto(
    Long id, Long lockExpirationThreshold, Long lockReservationThreshold, String packageName) {

  public static GitRepoSettingsDto fromGitRepoSettings(GitRepoSettings gitRepoSettings) {
    return new GitRepoSettingsDto(
        gitRepoSettings.getId(),
        gitRepoSettings.getLockExpirationThreshold(),
        gitRepoSettings.getLockReservationThreshold(),
        gitRepoSettings.getPackageName());
  }
}
