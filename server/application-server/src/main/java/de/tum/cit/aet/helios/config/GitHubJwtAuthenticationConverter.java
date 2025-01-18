package de.tum.cit.aet.helios.config;

import static java.util.stream.Collectors.toSet;

import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import de.tum.cit.aet.helios.github.permissions.PermissionException;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class GitHubJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  final String rolePrefix = "ROLE_";
  private final GitHubClient githubClient;

  @Autowired private HttpServletRequest request;

  public GitHubJwtAuthenticationConverter(GitHubClient githubClient) {
    this.githubClient = githubClient;
  }

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    String username = jwt.getClaim("preferred_username");
    String repositoryId = request.getHeader("X-Repository-Id");

    if (repositoryId != null) {
      try {
        authorities.addAll(getGithubRepositoryAuthorities(repositoryId, username));
      } catch (Exception e) {
        log.error("Failed to fetch GitHub repository permissions", e);
      }
    }

    return new JwtAuthenticationToken(jwt, authorities);
  }

  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    var resourceAccess = new HashMap<>(jwt.getClaim("resource_access"));

    var eternal = (Map<String, List<String>>) resourceAccess.get("account");

    var roles = eternal.get("roles");
    // Extract existing authorities from JWT token
    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    if (realmAccess != null) {
      return roles.stream()
          .map(role -> new SimpleGrantedAuthority("ROLE_PREFIX" + role.replace("-", "_")))
          .collect(toSet());
    }

    return authorities;
  }

  private Collection<GrantedAuthority> getGithubRepositoryAuthorities(
      String repositoryId, String username) throws PermissionException, IOException {

    Collection<GrantedAuthority> authorities = new ArrayList<>();

    // Hardcoded Helios developers
    String[] heliosDevelopers = {"gbanu", "thielpa", "egekocabas", "turkerkoc", "stefannemeth"};
    if (Arrays.asList(heliosDevelopers).contains(username)) {
      authorities.add(new SimpleGrantedAuthority(rolePrefix + RepoPermissionType.ADMIN));
      return authorities;
    }

    GitHubRepositoryRoleDto githubRepositoryRole =
        githubClient.getRepositoryPermissions(repositoryId, username);

    if (githubRepositoryRole.getPermission() == RepoPermissionType.ADMIN) {
      authorities.add(new SimpleGrantedAuthority(rolePrefix + RepoPermissionType.ADMIN));
    }
    if (githubRepositoryRole.getPermission() == RepoPermissionType.WRITE
        && githubRepositoryRole.getRoleName().equals("maintain")) {
      authorities.add(new SimpleGrantedAuthority(rolePrefix + "MAINTAINER"));
    }
    if (githubRepositoryRole.getPermission() == RepoPermissionType.WRITE
        && !githubRepositoryRole.getRoleName().equals("maintain")) {
      authorities.add(new SimpleGrantedAuthority(rolePrefix + RepoPermissionType.WRITE));
    }
    if (githubRepositoryRole.getPermission() == RepoPermissionType.READ) {
      authorities.add(new SimpleGrantedAuthority(rolePrefix + RepoPermissionType.READ));
    }
    if (githubRepositoryRole.getPermission() == RepoPermissionType.NONE) {
      authorities.add(new SimpleGrantedAuthority(rolePrefix + RepoPermissionType.NONE));
    }

    return authorities;
  }
}
