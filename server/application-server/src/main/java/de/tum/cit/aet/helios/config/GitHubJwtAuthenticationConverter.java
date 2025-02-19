package de.tum.cit.aet.helios.config;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import lombok.extern.log4j.Log4j2;
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
  private final GitHubService gitHubService;
  private final HttpServletRequest request;

  public GitHubJwtAuthenticationConverter(GitHubService gitHubService, HttpServletRequest request) {
    this.gitHubService = gitHubService;
    this.request = request;
  }

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    String username = jwt.getClaim("preferred_username");
    String repositoryId = request.getHeader("X-Repository-Id");

    if (repositoryId != null) {
      try {
        authorities.addAll(getGithubRepositoryAuthorities(repositoryId, username));
      } catch (IOException e) {
        log.error("Failed to fetch GitHub repository permissions", e);
      }
    }

    return new JwtAuthenticationToken(jwt, authorities);
  }

  private Collection<GrantedAuthority> getGithubRepositoryAuthorities(
      String repositoryId, String username) throws IOException {

    Collection<GrantedAuthority> authorities = new ArrayList<>();

    // Hardcoded Helios developers
    String[] heliosDevelopers = {
       "gbanu", "thielpa", "egekocabas", "turkerkoc", "stefannemeth",
       "bensofficial"
    };
    if (Arrays.asList(heliosDevelopers).contains(username)) {
      authorities.add(new SimpleGrantedAuthority(rolePrefix + RepoPermissionType.ADMIN));
      return authorities;
    }

    GitHubRepositoryRoleDto githubRepositoryRole =
        gitHubService.getRepositoryRole(repositoryId, username);

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
