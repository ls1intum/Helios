package de.tum.cit.aet.helios.permissions;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import de.tum.cit.aet.helios.github.permissions.PermissionException;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/user-permissions")
public class UserPermissionsController {
  private final GitHubService gitHubService;

  public UserPermissionsController(GitHubService gitHubService) {
    this.gitHubService = gitHubService;
  }

  @GetMapping()
  public ResponseEntity<GitHubRepositoryRoleDto> getUserPermissions() {
    try {
      GitHubRepositoryRoleDto permissions = gitHubService.getRepositoryRole();
      return ResponseEntity.ok(permissions);
    } catch (PermissionException | IOException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new GitHubRepositoryRoleDto(RepoPermissionType.fromString("none"), "none"));
    }
  }
}
