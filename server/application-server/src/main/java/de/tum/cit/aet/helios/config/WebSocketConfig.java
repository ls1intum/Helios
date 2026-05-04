package de.tum.cit.aet.helios.config;

import de.tum.cit.aet.helios.environment.ws.EnvironmentDeploymentWebSocketHandler;
import de.tum.cit.aet.helios.workflow.ws.WorkflowRunWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final WorkflowRunWebSocketHandler workflowRunWebSocketHandler;
  private final EnvironmentDeploymentWebSocketHandler environmentDeploymentWebSocketHandler;
  private final WebSocketJwtHandshakeInterceptor handshakeInterceptor;
  private final Environment environment;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(workflowRunWebSocketHandler, "/ws/workflow-runs")
        .addInterceptors(handshakeInterceptor)
        .setAllowedOrigins(allowedOrigins())
        .setAllowedOriginPatterns(allowedOriginPatterns());

    registry
        .addHandler(environmentDeploymentWebSocketHandler, "/ws/environments")
        .addInterceptors(handshakeInterceptor)
        .setAllowedOrigins(allowedOrigins())
        .setAllowedOriginPatterns(allowedOriginPatterns());
  }

  private String[] allowedOrigins() {
    if (environment.matchesProfiles("prod")) {
      return new String[] {"https://helios.aet.cit.tum.de"};
    }
    if (environment.matchesProfiles("staging")) {
      return new String[] {"https://helios-staging.aet.cit.tum.de"};
    }
    return new String[0];
  }

  private String[] allowedOriginPatterns() {
    if (environment.matchesProfiles("prod") || environment.matchesProfiles("staging")) {
      return new String[0];
    }
    return new String[] {
      "http://localhost", "http://localhost:*", "http://127.0.0.1", "http://127.0.0.1:*",
    };
  }
}
