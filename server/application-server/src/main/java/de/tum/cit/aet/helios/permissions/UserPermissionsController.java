package de.tum.cit.aet.helios.permissions;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/user-permissions")
public class UserPermissionsController {
  private final GitHubService gitHubService;

  @GetMapping()
  public ResponseEntity<GitHubRepositoryRoleDto> getUserPermissions() throws IOException {
    GitHubRepositoryRoleDto permissions = gitHubService.getRepositoryRole();
    return ResponseEntity.ok(permissions);
  }
}
