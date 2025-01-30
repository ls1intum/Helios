package de.tum.cit.aet.helios.auth;

import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import java.io.IOException;
import org.kohsuke.github.GHUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserRepository userRepository;
  private final GitHubFacade githubFacade;
  private final GitHubUserSyncService githubUserSyncService;

  public AuthService(
      UserRepository userRepository,
      GitHubFacade githubFacade,
      GitHubUserSyncService githubUserSyncService) {
    this.userRepository = userRepository;
    this.githubFacade = githubFacade;
    this.githubUserSyncService = githubUserSyncService;
  }

  /**
   * Retrieves the preferred username from the JWT. This is the GitHub username of the user.
   *
   * @return The preferred username if available, or throws an exception.
   */
  public String getPreferredUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      Jwt jwt = (Jwt) jwtAuthenticationToken.getToken();
      return jwt.getClaim("preferred_username");
    }

    throw new IllegalStateException("Unable to fetch preferred_username");
  }

  public String getUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      Jwt jwt = (Jwt) jwtAuthenticationToken.getToken();
      return jwt.getClaim("sub");
    }

    throw new IllegalStateException("Unable to fetch user ID");
  }

  public boolean isLoggedIn() {
    return SecurityContextHolder.getContext().getAuthentication() != null;
  }

  public String getGithubId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      Jwt jwt = (Jwt) jwtAuthenticationToken.getToken();
      return jwt.getClaim("github_id");
    }

    throw new IllegalStateException("Unable to fetch GitHub ID");
  }

  public User getUserFromGithubId() {
    String githubIdString = getGithubId();
    Long githubIdLong = Long.valueOf(githubIdString);
    // Check if user already exists in the database
    User curUser = userRepository.findById(githubIdLong).orElse(null);
    // If user exists, return it
    if (curUser != null) {
      return curUser;
    }
    // If user does not exist, fetch user from GitHub and save it to the database
    GHUser ghUser;
    try {
      ghUser = githubFacade.getUser(getPreferredUsername());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to fetch GitHub user");
    }
    if (ghUser == null) {
      throw new IllegalStateException("Unable to fetch GitHub user");
    }
    return githubUserSyncService.processUser(ghUser);
  }
}
