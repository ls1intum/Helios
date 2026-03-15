package de.tum.cit.aet.helios.permissions;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class RepositoryAuthorizationService {

  private final AuthService authService;
  private final GitHubService gitHubService;

  @Value("${helios.developers:}")
  private Set<String> heliosDevelopers;

  public boolean hasAdminAccess(Long repositoryId) {
    if (repositoryId == null) {
      return false;
    }

    try {
      String username = authService.getPreferredUsername();

      if (heliosDevelopers.contains(username)) {
        return true;
      }

      return gitHubService.getRepositoryRole(repositoryId.toString(), username).getPermission()
          == RepoPermissionType.ADMIN;
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
      log.warn(
          "Failed to evaluate admin access for repository {}: {}",
          repositoryId,
          e.getMessage());
      return false;
    }
  }
}
