package de.tum.cit.aet.helios.github.permissions;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GitHubRepositoryRoleDto {
  private final RepoPermissionType permission;
  private final String roleName;
}
