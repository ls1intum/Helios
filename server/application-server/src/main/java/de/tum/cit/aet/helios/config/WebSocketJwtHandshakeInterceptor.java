package de.tum.cit.aet.helios.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authenticates a WebSocket handshake by reading a JWT and a repository id from the {@code
 * Sec-WebSocket-Protocol} header (browsers cannot set custom headers on a WS upgrade request).
 *
 * <p>Expected client subprotocols:
 *
 * <ul>
 *   <li>{@code helios.v1} — required marker; echoed back by the server.
 *   <li>{@code helios.token.<jwt>} — the access token.
 *   <li>{@code helios.repo.<id>} — the repository id this connection is scoped to.
 * </ul>
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

  private static final String TOKEN_PREFIX = "helios.token.";
  private static final String REPO_PREFIX = "helios.repo.";

  private final JwtDecoder jwtDecoder;

  @Value("${helios.developers:}")
  private Set<String> heliosDevelopers;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {

    List<String> subprotocols = request.getHeaders().get("Sec-WebSocket-Protocol");
    if (subprotocols == null || subprotocols.isEmpty()) {
      reject(response, "missing Sec-WebSocket-Protocol header");
      return false;
    }

    String token = null;
    String repoId = null;
    boolean hasMarker = false;
    for (String header : subprotocols) {
      for (String raw : header.split(",")) {
        String entry = raw.trim();
        if ("helios.v1".equals(entry)) {
          hasMarker = true;
        } else if (entry.startsWith(TOKEN_PREFIX)) {
          token = entry.substring(TOKEN_PREFIX.length());
        } else if (entry.startsWith(REPO_PREFIX)) {
          repoId = entry.substring(REPO_PREFIX.length());
        }
      }
    }

    if (!hasMarker || token == null || token.isBlank() || repoId == null || repoId.isBlank()) {
      reject(response, "missing required subprotocols");
      return false;
    }

    Jwt jwt;
    try {
      jwt = jwtDecoder.decode(token);
    } catch (JwtException e) {
      log.debug("WS JWT decode failed: {}", e.getMessage());
      reject(response, "invalid token");
      return false;
    }

    String username = jwt.getClaim("preferred_username");
    if (username == null || username.isBlank()) {
      reject(response, "token missing preferred_username");
      return false;
    }

    attributes.put(WebSocketSessionAttributes.USERNAME, username);
    attributes.put(WebSocketSessionAttributes.REPOSITORY_ID, repoId);
    attributes.put(
        WebSocketSessionAttributes.IS_DEVELOPER,
        heliosDevelopers != null && heliosDevelopers.contains(username));
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // no-op
  }

  private static void reject(ServerHttpResponse response, String reason) {
    log.debug("WS handshake rejected: {}", reason);
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
  }
}
