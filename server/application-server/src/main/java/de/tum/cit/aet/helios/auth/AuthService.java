package de.tum.cit.aet.helios.auth;

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
} 