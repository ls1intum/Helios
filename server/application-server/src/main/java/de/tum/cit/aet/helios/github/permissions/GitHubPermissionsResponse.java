package de.tum.cit.aet.helios.github.permissions;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitHubPermissionsResponse {
  private String permission;
  private Object user;

  @JsonProperty("role_name")
  private String roleName;
}
