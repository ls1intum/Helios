package de.tum.cit.aet.helios.auth;

import de.tum.cit.aet.helios.environment.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

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

  public boolean hasRole(String role) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
  }

  public boolean canDeployToEnvironment(Environment.Type environmentType) {
    if (null != environmentType) {
      switch (environmentType) {
        case PRODUCTION -> {
          return hasRole("ROLE_ADMIN");
        }
        case STAGING -> {
          return hasRole("ROLE_MAINTAINER") || hasRole("ROLE_ADMIN");
        }
        case TEST -> {
          return hasRole("ROLE_WRITE") || hasRole("ROLE_MAINTAINER") || hasRole("ROLE_ADMIN");
        }
        default -> {}
      }
    }
    return false;
  }
}
