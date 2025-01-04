package de.tum.cit.aet.helios.permission;

import de.tum.cit.aet.helios.github.GitHubService;
import org.kohsuke.github.GHPermissionType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/user-permission")
public class UserPermissionController {
  private GitHubService gitHubService;

  public UserPermissionController(GitHubService gitHubService) {
    this.gitHubService = gitHubService;
  }

  @GetMapping("/repository/{repoId}")
  public ResponseEntity<GHPermissionType> getRepoPermissions(
      @RequestHeader("Authorization") String token, @PathVariable Long repoId) {
    return ResponseEntity.ok(gitHubService.getPermissionForRepository(repoId, token));
  }
}
