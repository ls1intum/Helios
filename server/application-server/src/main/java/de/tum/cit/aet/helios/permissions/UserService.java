package de.tum.cit.aet.helios.permissions;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import jakarta.transaction.Transactional;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
  private final GitHubService githubService;

  public boolean isAtLeastMaintainer() {
    try {
      GitHubRepositoryRoleDto githubRepositoryRole = githubService.getRepositoryRole();
      return (githubRepositoryRole.getPermission() == RepoPermissionType.WRITE
              && githubRepositoryRole.getRoleName().equals("maintain"))
          || githubRepositoryRole.getPermission().equals(RepoPermissionType.ADMIN);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }
}
